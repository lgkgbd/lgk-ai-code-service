package com.lgk.lgkaicodeservice.service.word.ocr;

import com.lgk.lgkaicodeservice.constant.RedisConstant;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.mapper.UserWordReviewLogMapper;
import com.lgk.lgkaicodeservice.mapper.UserWordSightingMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookItemMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookMapper;
import com.lgk.lgkaicodeservice.model.dto.word.WordCaptureRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.entity.UserWord;
import com.lgk.lgkaicodeservice.model.entity.UserWordReviewLog;
import com.lgk.lgkaicodeservice.model.entity.UserWordSighting;
import com.lgk.lgkaicodeservice.model.entity.WordBook;
import com.lgk.lgkaicodeservice.model.entity.WordBookItem;
import com.lgk.lgkaicodeservice.model.entity.WordDict;
import com.lgk.lgkaicodeservice.model.enums.WordOcrStatusEnum;
import com.lgk.lgkaicodeservice.model.vo.WordCardVO;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import com.lgk.lgkaicodeservice.model.vo.WordOcrTaskVO;
import com.lgk.lgkaicodeservice.service.WordDictService;
import com.lgk.lgkaicodeservice.service.WordOcrService;
import com.lgk.lgkaicodeservice.service.WordService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拍照录入全链路端到端测试（真实调用 qwen3-vl-plus，会产生 API 费用）
 * <p>
 * 前面几个测试各管一段：解析测试管脏输出，任务流转测试管状态机（打桩模型），
 * 实调测试管识别准确率。它们全绿也<b>不能证明这些环节串起来是通的</b>——
 * 本测试就是来堵这个缺口的：一张图从上传走到数据库落库，中间不打任何桩。
 * <p>
 * 覆盖链路：
 * <pre>
 *   上传图片 → 建异步任务 → 压缩 → 存 MinIO → 真实 VL 识别 → 轮询到 SUCCEED
 *   → 模拟用户确认 → capture 入库 → 校验 user_word / user_word_sighting / word_dict
 * </pre>
 */
@SpringBootTest
class WordOcrEndToEndTest {

    private static final long USER_E2E = 990000301L;

    /**
     * 轮询上限。真实 VL 单张 2~5 秒，给到 90 秒足够容忍网络抖动
     */
    private static final long POLL_TIMEOUT_MS = 90_000L;

    @Resource
    private WordOcrService wordOcrService;

    @Resource
    private WordService wordService;

    @Resource
    private WordDictService wordDictService;

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

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_E2E);
        user.setUserName("e2e-" + USER_E2E);
        user.setUserRole("user");
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("★ 全链路：拍照上传 → 异步识别 → 确认 → 落库，一步不打桩")
    void fullFlowFromPhotoToDatabase() throws Exception {
        // ---------- 1. 上传：用手机尺寸的大图，让压缩链路真正参与 ----------
        byte[] photo = StickyNoteImageFixture.generatePhoneSized();
        System.out.println("上传图片：" + photo.length / 1024 + "KB");
        assertTrue(photo.length > 200 * 1024, "测试图应足够大以触发压缩，否则测不到压缩链路");

        MockMultipartFile file = new MockMultipartFile(
                "files", "sticky-note.jpg", "image/jpeg", photo);

        long submitStart = System.currentTimeMillis();
        String taskId = wordOcrService.submit(new MockMultipartFile[]{file}, user);
        long submitCost = System.currentTimeMillis() - submitStart;
        assertNotNull(taskId);

        // 提交必须立刻返回——这正是做成异步任务的全部意义，用户不能卡在这等 VL
        System.out.println("submit 耗时：" + submitCost + "ms");
        assertTrue(submitCost < 3000,
                "submit 耗时 " + submitCost + "ms，异步失效了，用户会被卡住");

        // ---------- 2. 轮询：模拟前端每秒问一次 ----------
        WordOcrTaskVO task = pollUntilFinal(taskId);
        System.out.println("任务终态：" + task.getStatus() + "，耗时 "
                + (System.currentTimeMillis() - submitStart) + "ms");
        assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus(),
                "识别失败：" + task.getErrorMsg());
        assertEquals(task.getTotalImages(), task.getDoneImages(), "进度未推进到满");

        List<WordOcrItemVO> items = task.getItems();
        assertNotNull(items);
        System.out.println("识别出 " + items.size() + " 个候选词：");
        items.forEach(i -> System.out.printf("  %-16s %s%n", i.getWord(), i.getTranslation()));
        assertFalse(items.isEmpty(), "一个词都没识别出来");

        // 期望词的召回
        List<String> expected = StickyNoteImageFixture.DEFAULT_ENTRIES.stream()
                .map(StickyNoteImageFixture.Entry::word).toList();
        Set<String> got = items.stream().map(WordOcrItemVO::getWord).collect(Collectors.toSet());
        long hit = expected.stream().filter(got::contains).count();
        System.out.printf("召回 %d/%d%n", hit, expected.size());
        assertTrue(hit >= expected.size() * 0.75,
                "召回率过低，只命中 " + hit + "/" + expected.size());

        // 原图应已存进 MinIO 并拿到短链（存图失败会被吞掉，故只做软校验并打印）
        System.out.println("原图短链：" + task.getImageUrls());

        // ---------- 3. 确认：模拟用户在前端勾选后提交 ----------
        Map<String, String> translations = new HashMap<>();
        items.forEach(i -> {
            if (StringUtils.hasText(i.getTranslation())) {
                translations.put(i.getWord(), i.getTranslation());
            }
        });
        WordCaptureRequest request = new WordCaptureRequest();
        request.setText(items.stream().map(WordOcrItemVO::getWord).collect(Collectors.joining(" ")));
        request.setChannel(WordConstant.CHANNEL_OCR);
        request.setTranslations(translations);
        request.setImageUrl(task.getImageUrls().isEmpty() ? null : task.getImageUrls().get(0));

        List<WordCardVO> cards = wordService.capture(request, user);
        assertEquals(items.size(), cards.size(), "识别出的词应当全部入库");
        cards.forEach(c -> assertTrue(c.getNewlyAdded(), c.getSpelling() + " 应是新词"));

        // ---------- 4. 校验落库结果 ----------
        List<UserWord> saved = wordService.list(QueryWrapper.create()
                .eq(UserWord::getUserId, USER_E2E));
        assertEquals(items.size(), saved.size(), "user_word 落库条数不符");

        // 每个词的 note 都必须符合 B 方案：词典有释义→note 空；词典没有→note 是手写释义
        int noteFromHandwriting = 0;
        int deferredToDict = 0;
        for (UserWord w : saved) {
            WordDict dict = wordDictService.getBySpelling(w.getSpelling(), WordConstant.DEFAULT_LANG);
            assertNotNull(dict, "词条应已落库：" + w.getSpelling());
            boolean dictHasTranslation = StringUtils.hasText(dict.getTranslation());
            String handwritten = translations.get(w.getSpelling());

            if (dictHasTranslation) {
                assertFalse(StringUtils.hasText(w.getNote()),
                        w.getSpelling() + "：词典已有释义，note 必须为空，实际=" + w.getNote());
                deferredToDict++;
            } else if (StringUtils.hasText(handwritten)) {
                assertEquals(handwritten, w.getNote(),
                        w.getSpelling() + "：词典无释义，note 应等于手写释义");
                noteFromHandwriting++;
            }
        }
        System.out.printf("B 方案落地：%d 个词以词典释义为准，%d 个词用了手写释义%n",
                deferredToDict, noteFromHandwriting);

        // ★ 共享词典绝不能被手写释义污染
        for (Map.Entry<String, String> e : translations.entrySet()) {
            WordDict dict = wordDictService.getBySpelling(e.getKey(), WordConstant.DEFAULT_LANG);
            if (dict != null && StringUtils.hasText(dict.getTranslation())) {
                assertFalse(dict.getTranslation().contains(e.getValue())
                                && !dict.getSource().equals("ecdict"),
                        e.getKey() + " 的手写释义疑似被写进了共享词典");
            }
        }

        // 遇见记录带上了原图，日后能溯源回这张便利贴
        List<UserWordSighting> sightings = sightingMapper.selectListByQuery(QueryWrapper.create()
                .eq(UserWordSighting::getUserId, USER_E2E));
        if (!task.getImageUrls().isEmpty()) {
            assertEquals(items.size(), sightings.size(), "每个词都应有一条带原图的遇见记录");
            sightings.forEach(s -> {
                assertEquals(WordConstant.CHANNEL_OCR, s.getChannel());
                assertEquals(task.getImageUrls().get(0), s.getSourceUrl());
            });
        }

        // 全部进了待复习队列，拍完就能直接开始背
        long queued = redissonClient.getScoredSortedSet(
                RedisConstant.getWordDueKey(USER_E2E)).size();
        assertEquals(items.size(), queued, "录入的词应全部进入待复习队列");
        System.out.println("待复习队列词数：" + queued);
    }

    @Test
    @DisplayName("多张图一次提交：结果跨图合并去重后统一入库")
    void multipleImagesMergeAndImport() throws Exception {
        // 两张内容不同的便利贴，其中 resilient 故意重复出现，用于验证跨图去重
        List<StickyNoteImageFixture.Entry> page1 = List.of(
                new StickyNoteImageFixture.Entry("resilient", "有韧性的"),
                new StickyNoteImageFixture.Entry("candid", "坦率的"));
        List<StickyNoteImageFixture.Entry> page2 = List.of(
                new StickyNoteImageFixture.Entry("resilient", "有韧性的"),
                new StickyNoteImageFixture.Entry("prudent", "审慎的"));

        MockMultipartFile[] files = {
                new MockMultipartFile("files", "p1.jpg", "image/jpeg",
                        StickyNoteImageFixture.generate(page1, 7L)),
                new MockMultipartFile("files", "p2.jpg", "image/jpeg",
                        StickyNoteImageFixture.generate(page2, 9L)),
        };

        String taskId = wordOcrService.submit(files, user);
        WordOcrTaskVO task = pollUntilFinal(taskId);
        assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus(),
                "识别失败：" + task.getErrorMsg());
        assertEquals(2, task.getTotalImages());

        List<String> words = task.getItems().stream().map(WordOcrItemVO::getWord).toList();
        System.out.println("两张图合并后：" + words);

        // 去重：resilient 出现在两张图上，合并后只能有一个
        assertEquals(words.size(), Set.copyOf(words).size(), "跨图去重失效，出现重复词：" + words);
        assertTrue(words.contains("resilient"), "两张图都有的词反而丢了");
    }

    /**
     * 模拟前端轮询，直到任务进入终态或超时
     */
    private WordOcrTaskVO pollUntilFinal(String taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        WordOcrTaskVO task = null;
        while (System.currentTimeMillis() < deadline) {
            task = wordOcrService.getTask(taskId, user);
            WordOcrStatusEnum status = WordOcrStatusEnum.getEnumByValue(task.getStatus());
            if (status != null && status.isFinal()) {
                return task;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("轮询超时，任务未进入终态，最后状态="
                + (task == null ? "null" : task.getStatus()));
    }

    /**
     * 硬清理该测试用户的所有痕迹，保证用例可重复执行
     */
    private void cleanUp() {
        LogicDeleteManager.execWithoutLogicDelete(() -> {
            List<Long> bookIds = wordBookMapper.selectListByQuery(QueryWrapper.create()
                            .eq(WordBook::getOwnerId, USER_E2E))
                    .stream().map(WordBook::getId).toList();
            if (!bookIds.isEmpty()) {
                wordBookItemMapper.deleteByQuery(QueryWrapper.create()
                        .in(WordBookItem::getBookId, bookIds));
            }
            wordBookMapper.deleteByQuery(QueryWrapper.create()
                    .eq(WordBook::getOwnerId, USER_E2E));
            wordService.getMapper().deleteByQuery(QueryWrapper.create()
                    .eq(UserWord::getUserId, USER_E2E));
            sightingMapper.deleteByQuery(QueryWrapper.create()
                    .eq(UserWordSighting::getUserId, USER_E2E));
            reviewLogMapper.deleteByQuery(QueryWrapper.create()
                    .eq(UserWordReviewLog::getUserId, USER_E2E));
            return null;
        });
        redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(USER_E2E)).delete();
    }
}
