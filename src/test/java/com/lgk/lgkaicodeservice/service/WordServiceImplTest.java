package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.constant.RedisConstant;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.mapper.UserWordReviewLogMapper;
import com.lgk.lgkaicodeservice.mapper.UserWordSightingMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookItemMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookMapper;
import com.lgk.lgkaicodeservice.model.dto.word.WordCaptureRequest;
import com.lgk.lgkaicodeservice.model.entity.*;
import com.lgk.lgkaicodeservice.model.enums.MasteryEnum;
import com.lgk.lgkaicodeservice.model.enums.ReviewQualityEnum;
import com.lgk.lgkaicodeservice.model.vo.WordCardVO;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务层测试，覆盖 docs/单词记忆功能-开发计划.md 6.2 节场景。
 * <p>
 * 两条核心回归用例：
 * <ul>
 *   <li>重复录入同一个词 → user_word 只有 1 条，encounterNum=2</li>
 *   <li>同一个词既在生词本又在六级词书 → user_word 仍只 1 条</li>
 * </ul>
 * 使用独立的合成 userId + 每次硬清理，保证可重复执行（word 表无外键约束）。
 */
@SpringBootTest
class WordServiceImplTest {

    private static final long USER_A = 990000001L;
    private static final long USER_B = 990000002L;
    /** 系统预置六级词书 id（sql/word_memory.sql 初始化） */
    private static final long CET6_BOOK_ID = 1L;

    @Resource
    private WordService wordService;

    @Resource
    private WordReviewService wordReviewService;

    @Resource
    private UserWordSightingMapper sightingMapper;

    @Resource
    private UserWordReviewLogMapper reviewLogMapper;

    @Resource
    private WordBookMapper wordBookMapper;

    @Resource
    private WordBookItemMapper wordBookItemMapper;

    @Resource
    private RedissonClient redissonClient;

    private User userA;
    private User userB;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUserName("test-" + id);
        u.setUserRole("user");
        return u;
    }

    @BeforeEach
    void setUp() {
        userA = user(USER_A);
        userB = user(USER_B);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    /**
     * 硬清理测试用户的所有痕迹（绕过逻辑删除，保证唯一索引不残留软删行）
     */
    private void cleanUp() {
        LogicDeleteManager.execWithoutLogicDelete(() -> {
            // 先删系统词书里为测试插入的关联，再删个人词书本体与关联
            List<Long> personalBookIds = wordBookMapper.selectListByQuery(QueryWrapper.create()
                            .in(WordBook::getOwnerId, List.of(USER_A, USER_B)))
                    .stream().map(WordBook::getId).collect(Collectors.toList());

            // 删测试往六级书塞的词（仅测试期间插入的，靠 dictId 属于测试用户的 user_word 反查）
            List<Long> dictIds = ((com.lgk.lgkaicodeservice.mapper.UserWordMapper) wordService.getMapper())
                    .selectListByQuery(QueryWrapper.create()
                            .in(UserWord::getUserId, List.of(USER_A, USER_B)))
                    .stream().map(UserWord::getDictId).distinct().collect(Collectors.toList());
            if (!dictIds.isEmpty()) {
                wordBookItemMapper.deleteByQuery(QueryWrapper.create()
                        .eq(WordBookItem::getBookId, CET6_BOOK_ID)
                        .in(WordBookItem::getDictId, dictIds));
            }
            if (!personalBookIds.isEmpty()) {
                wordBookItemMapper.deleteByQuery(QueryWrapper.create()
                        .in(WordBookItem::getBookId, personalBookIds));
            }
            wordBookMapper.deleteByQuery(QueryWrapper.create()
                    .in(WordBook::getOwnerId, List.of(USER_A, USER_B)));
            wordService.getMapper().deleteByQuery(QueryWrapper.create()
                    .in(UserWord::getUserId, List.of(USER_A, USER_B)));
            sightingMapper.deleteByQuery(QueryWrapper.create()
                    .in(UserWordSighting::getUserId, List.of(USER_A, USER_B)));
            reviewLogMapper.deleteByQuery(QueryWrapper.create()
                    .in(UserWordReviewLog::getUserId, List.of(USER_A, USER_B)));
            return null;
        });
        redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(USER_A)).delete();
        redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(USER_B)).delete();
    }

    private WordCaptureRequest req(String text, String sentence) {
        WordCaptureRequest r = new WordCaptureRequest();
        r.setText(text);
        r.setSentence(sentence);
        r.setChannel(sentence == null ? WordConstant.CHANNEL_MANUAL : WordConstant.CHANNEL_PASTE);
        return r;
    }

    private long countUserWords(long userId) {
        return wordService.count(QueryWrapper.create().eq(UserWord::getUserId, userId));
    }

    private UserWord onlyWord(long userId) {
        List<UserWord> list = wordService.list(QueryWrapper.create().eq(UserWord::getUserId, userId));
        assertEquals(1, list.size(), "应当只有 1 条 user_word");
        return list.get(0);
    }

    // ==================== 场景 1：首次录入新词 ====================

    @Test
    @DisplayName("首次录入新词：user_word +1，encounterNum=1，mastery=0，Redis ZSet 有该成员")
    void captureNewWord() {
        List<WordCardVO> cards = wordService.capture(req("serendipity", null), userA);
        assertEquals(1, cards.size());
        WordCardVO card = cards.get(0);
        assertEquals("serendipity", card.getSpelling());
        assertTrue(card.getNewlyAdded());
        assertEquals(1, card.getEncounterNum());
        assertEquals(MasteryEnum.NEW.getValue(), card.getMastery());

        UserWord w = onlyWord(USER_A);
        assertEquals(1, w.getEncounterNum());
        assertEquals(MasteryEnum.NEW.getValue(), w.getMastery());
        assertNotNull(w.getDueTime());

        RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(USER_A));
        assertTrue(queue.contains(w.getId()), "新词应立即入待复习队列");
    }

    // ==================== 场景 2：重复录入（★核心回归）====================

    @Test
    @DisplayName("★ 重复录入同一个词：不新增记录，encounterNum=2，sighting +1")
    void captureDuplicate() {
        wordService.capture(req("serendipity", "First time I saw serendipity here."), userA);
        List<WordCardVO> second = wordService.capture(req("serendipity", "Again serendipity struck."), userA);

        assertEquals(1, second.size());
        assertFalse(second.get(0).getNewlyAdded(), "第二次应标记为非新增");
        assertEquals(2, second.get(0).getEncounterNum());

        assertEquals(1, countUserWords(USER_A), "重复录入不得新增 user_word");
        UserWord w = onlyWord(USER_A);
        assertEquals(2, w.getEncounterNum());

        long sightings = sightingMapper.selectCountByQuery(QueryWrapper.create()
                .eq(UserWordSighting::getUserWordId, w.getId()));
        assertEquals(2, sightings, "两次带原句录入应有 2 条 sighting");
    }

    // ==================== 场景 3：并发录入同一个词 ====================

    @Test
    @DisplayName("并发录入同一个词：唯一索引拦截，最终仅 1 条，encounterNum 正确")
    void captureConcurrent() throws InterruptedException {
        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    wordService.capture(req("epiphany", null), userA);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        ready.await();
        go.countDown();
        done.await();

        assertEquals(0, errors.get(), "并发录入不应抛异常");
        assertEquals(1, countUserWords(USER_A), "唯一索引应保证最终只有 1 条");
        assertEquals(threads, onlyWord(USER_A).getEncounterNum(), "encounterNum 应等于并发次数");
    }

    // ==================== 场景 4：批量录入 50 词 ====================

    @Test
    @DisplayName("批量录入 50 词：全部成功，返回值区分新增/已存在")
    void captureBatch50() {
        String text = IntStream.range(0, 50)
                .mapToObj(i -> "zzbatch" + (char) ('a' + i / 26) + (char) ('a' + i % 26))
                .collect(Collectors.joining(", "));
        List<WordCardVO> cards = wordService.capture(req(text, null), userA);
        assertEquals(50, cards.size());
        assertTrue(cards.stream().allMatch(WordCardVO::getNewlyAdded), "首次批量全部为新增");
        assertEquals(50, countUserWords(USER_A));

        // 再录前 3 个 → 应标记为已存在
        List<WordCardVO> again = wordService.capture(req("zzbatchaa, zzbatchab, zzbatchac", null), userA);
        assertTrue(again.stream().noneMatch(WordCardVO::getNewlyAdded));
        assertEquals(50, countUserWords(USER_A), "重复录入不应改变总数");
    }

    // ==================== 场景 5：提交复习 quality=0 ====================

    @Test
    @DisplayName("提交复习 quality=0：dueTime 回到当天，lapseCount+1，重新入队")
    void reviewForgot() {
        wordService.capture(req("adapt", null), userA);
        UserWord w = onlyWord(USER_A);

        Integer mastery = wordReviewService.submit(w.getId(), ReviewQualityEnum.FORGOT.getValue(), 1500, userA);
        assertEquals(MasteryEnum.LEARNING.getValue(), mastery);

        UserWord after = wordService.getById(w.getId());
        assertEquals(1, after.getLapseCount());
        assertEquals(1, after.getIntervalDays(), "答错间隔归 1");
        assertTrue(!after.getDueTime().toLocalDate().isAfter(LocalDate.now()), "dueTime 应回到今天（或更早）");

        RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(USER_A));
        assertTrue(queue.contains(w.getId()), "答错应重新入队");

        long logs = reviewLogMapper.selectCountByQuery(QueryWrapper.create()
                .eq(UserWordReviewLog::getUserWordId, w.getId()));
        assertEquals(1, logs, "应写入 1 条复习日志");
    }

    // ==================== 场景 6：连续答对至毕业 ====================

    @Test
    @DisplayName("连续答对至 intervalDays>=60：mastery=3，移出 Redis 队列")
    void reviewGraduate() {
        wordService.capture(req("adept", null), userA);
        UserWord w = onlyWord(USER_A);

        Integer mastery = MasteryEnum.NEW.getValue();
        // 间隔梯度 1/2/4/7/15/30/60 —— 7 次答对到 60 天毕业
        for (int i = 0; i < 7; i++) {
            mastery = wordReviewService.submit(w.getId(), ReviewQualityEnum.RECALLED.getValue(), 800, userA);
        }
        assertEquals(MasteryEnum.MASTERED.getValue(), mastery, "连续答对 7 次应毕业");

        UserWord after = wordService.getById(w.getId());
        assertEquals(MasteryEnum.MASTERED.getValue(), after.getMastery());
        assertTrue(after.getIntervalDays() >= WordConstant.GRADUATE_INTERVAL_DAYS);

        RScoredSortedSet<Long> queue = redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(USER_A));
        assertFalse(queue.contains(w.getId()), "已掌握应移出复习队列");
    }

    // ==================== 场景 7：跨用户越权 ====================

    @Test
    @DisplayName("跨用户越权：A 提交 B 的 userWordId → NO_AUTH_ERROR")
    void reviewCrossUserDenied() {
        wordService.capture(req("serendipity", null), userA);
        UserWord wa = onlyWord(USER_A);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wordReviewService.submit(wa.getId(), ReviewQualityEnum.RECALLED.getValue(), 500, userB));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    // ==================== 场景 8：一词多书（★核心回归）====================

    @Test
    @DisplayName("★ 同一个词既在生词本又在六级词书：user_word 仍只有 1 条，复习进度共享")
    void sameWordInPersonalAndSystemBook() {
        // 录入生词本
        wordService.capture(req("serendipity", null), userA);
        UserWord w = onlyWord(USER_A);
        Long dictId = w.getDictId();

        // 手动把同一个词条也放进六级词书（模拟它同时是六级词）
        wordBookItemMapper.insert(WordBookItem.builder()
                .bookId(CET6_BOOK_ID).dictId(dictId).seq(0)
                .createTime(java.time.LocalDateTime.now()).build());

        // 再次录入同一个词
        List<WordCardVO> again = wordService.capture(req("serendipity", null), userA);
        assertFalse(again.get(0).getNewlyAdded());

        // 关键断言：user_word 对 (userId, dictId) 仍然只有 1 条
        assertEquals(1, countUserWords(USER_A), "一词多书绝不能产生两条学习记录");
        long sameDict = wordService.count(QueryWrapper.create()
                .eq(UserWord::getUserId, USER_A)
                .eq(UserWord::getDictId, dictId));
        assertEquals(1, sameDict);

        // 词条确实同时被两本书引用
        long books = wordBookItemMapper.selectCountByQuery(QueryWrapper.create()
                .eq(WordBookItem::getDictId, dictId));
        assertTrue(books >= 2, "该词条应同时被生词本与六级书引用");
    }
}
