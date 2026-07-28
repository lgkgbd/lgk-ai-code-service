package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.mapper.UserWordMapper;
import com.lgk.lgkaicodeservice.mapper.UserWordSightingMapper;
import com.lgk.lgkaicodeservice.model.dto.word.WordCaptureRequest;
import com.lgk.lgkaicodeservice.model.dto.word.WordQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.*;
import com.lgk.lgkaicodeservice.model.enums.MasteryEnum;
import com.lgk.lgkaicodeservice.model.vo.WordCardVO;
import com.lgk.lgkaicodeservice.model.vo.WordSightingVO;
import com.lgk.lgkaicodeservice.model.vo.WordStatisticsVO;
import com.lgk.lgkaicodeservice.service.WordBookService;
import com.lgk.lgkaicodeservice.service.WordDictService;
import com.lgk.lgkaicodeservice.service.WordService;
import com.lgk.lgkaicodeservice.service.word.WordDueQueue;
import com.lgk.lgkaicodeservice.utils.WordTextParser;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 单词学习服务实现（录入主链路核心）
 */
@Slf4j
@Service
public class WordServiceImpl extends ServiceImpl<UserWordMapper, UserWord> implements WordService {

    @Resource
    private WordTextParser wordTextParser;

    @Resource
    private WordDictService wordDictService;

    @Resource
    private WordBookService wordBookService;

    @Resource
    private UserWordSightingMapper sightingMapper;

    @Resource
    private WordDueQueue dueQueue;

    // ==================== 录入主链路 ====================

    @Override
    public List<WordCardVO> capture(WordCaptureRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!StringUtils.hasText(request.getText()), ErrorCode.PARAMS_ERROR, "录入内容不能为空");
        long userId = loginUser.getId();

        // 1. 拆词（去重、转小写、剔标点、50 词上限）
        WordTextParser.ParseResult parsed = wordTextParser.parse(request.getText());
        if (parsed.isEmpty()) {
            return new ArrayList<>();
        }
        if (parsed.isTruncated()) {
            log.info("录入超过 {} 词上限，已截断 {} 个，userId={}",
                    WordConstant.MAX_CAPTURE_WORDS, parsed.getDroppedCount(), userId);
        }

        // 2. 确保个人生词本存在（懒创建）。首次录入并发下 word_book 无唯一索引，串行化兜底
        WordBook personalBook = getPersonalBookSafely(userId);

        String sentence = truncate(request.getSentence(), WordConstant.MAX_SENTENCE_LENGTH);
        String channel = StringUtils.hasText(request.getChannel())
                ? request.getChannel() : WordConstant.CHANNEL_MANUAL;

        // 3. 逐词处理，单个词失败不影响其余（批量 50 词的健壮性）
        List<WordCardVO> cards = new ArrayList<>();
        for (String rawWord : parsed.getWords()) {
            try {
                cards.add(captureOne(rawWord, userId, personalBook.getId(),
                        sentence, request.getSourceTitle(), request.getSourceUrl(), channel));
            } catch (Exception e) {
                log.error("录入单词失败，跳过，word={}, userId={}", rawWord, userId, e);
            }
        }
        return cards;
    }

    /**
     * 录入单个词。这里是幂等 + 并发安全的关键：
     * <ol>
     *   <li>词形还原到原形（running→run），查不到按原样</li>
     *   <li>解析并落库 word_dict（责任链，永不为 null）</li>
     *   <li>insert user_word；靠 uq_user_dict 唯一索引判重，撞了就 encounterNum+1（不先查后插）</li>
     *   <li>有原句则追加一条 sighting</li>
     *   <li>写 Redis 待复习队列</li>
     * </ol>
     */
    private WordCardVO captureOne(String rawWord, long userId, long bookId,
                                  String sentence, String sourceTitle, String sourceUrl, String channel) {
        String lemma = wordTextParser.restoreLemma(rawWord);
        WordDict dict = wordDictService.resolveAndSave(lemma, WordConstant.DEFAULT_LANG);

        LocalDateTime now = LocalDateTime.now();
        boolean newlyAdded;
        UserWord userWord;

        UserWord candidate = UserWord.builder()
                .userId(userId)
                .dictId(dict.getId())
                .spelling(dict.getSpelling())
                .mastery(MasteryEnum.NEW.getValue())
                .easeFactor(WordConstant.DEFAULT_EASE_FACTOR)
                .intervalDays(0)
                .reviewCount(0)
                .lapseCount(0)
                .encounterNum(1)
                // 新词立即可复习
                .dueTime(now)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        try {
            this.save(candidate);
            newlyAdded = true;
            userWord = candidate;
        } catch (DuplicateKeyException e) {
            // 已存在（含此前被软删的）：encounterNum+1，并顺带复活软删行
            newlyAdded = false;
            LogicDeleteManager.execWithoutLogicDelete(() -> UpdateChain.of(UserWord.class)
                    .setRaw("encounterNum", "encounterNum + 1")
                    .set(UserWord::getIsDelete, 0)
                    .set(UserWord::getUpdateTime, LocalDateTime.now())
                    .where(UserWord::getUserId).eq(userId)
                    .and(UserWord::getDictId).eq(dict.getId())
                    .update());
            userWord = LogicDeleteManager.execWithoutLogicDelete(() -> this.getOne(QueryWrapper.create()
                    .eq(UserWord::getUserId, userId)
                    .eq(UserWord::getDictId, dict.getId())));
        }

        // 词加入个人生词本（幂等）
        wordBookService.addWord(bookId, dict.getId());

        // 有原句才留 sighting；纯单个词无上下文，复习时用词典例句兜底
        if (StringUtils.hasText(sentence)) {
            UserWordSighting sighting = UserWordSighting.builder()
                    .userWordId(userWord.getId())
                    .userId(userId)
                    .sentence(sentence)
                    .sourceTitle(truncate(sourceTitle, 256))
                    .sourceUrl(truncate(sourceUrl, WordConstant.MAX_SENTENCE_LENGTH))
                    .channel(channel)
                    .createTime(LocalDateTime.now())
                    .build();
            sightingMapper.insert(sighting);
        }

        // 写待复习队列（新词=now；已存在则沿用其 dueTime，不打乱复习节奏）
        dueQueue.upsert(userId, userWord.getId(), userWord.getDueTime());

        return toCard(userWord, dict, newlyAdded, null);
    }

    /**
     * 个人生词本懒创建。word_book 无 (ownerId,type) 唯一索引，用 intern 串行化避免首次并发建两本
     */
    private WordBook getPersonalBookSafely(long userId) {
        synchronized (("word:book:personal:" + userId).intern()) {
            return wordBookService.getOrCreatePersonalBook(userId);
        }
    }

    // ==================== 我的词库 ====================

    @Override
    public Page<WordCardVO> listMyWords(WordQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long userId = loginUser.getId();
        long pageNum = request.getPageNum();
        long pageSize = request.getPageSize();
        ThrowUtils.throwIf(pageSize > 50, ErrorCode.PARAMS_ERROR, "单页最多 50 条");

        QueryWrapper qw = QueryWrapper.create()
                .eq(UserWord::getUserId, userId);
        if (request.getMastery() != null) {
            qw.eq(UserWord::getMastery, request.getMastery());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            qw.like(UserWord::getSpelling, request.getKeyword().trim().toLowerCase() + "%");
        }
        qw.orderBy(UserWord::getCreateTime, false);

        Page<UserWord> page = this.page(new Page<>(pageNum, pageSize), qw);
        Page<WordCardVO> voPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        voPage.setRecords(joinDict(page.getRecords()));
        return voPage;
    }

    /**
     * 批量补齐词条信息，避免 N+1
     */
    private List<WordCardVO> joinDict(List<UserWord> words) {
        if (CollUtil.isEmpty(words)) {
            return new ArrayList<>();
        }
        List<Long> dictIds = words.stream().map(UserWord::getDictId).distinct().collect(Collectors.toList());
        Map<Long, WordDict> dictMap = wordDictService.listByIds(dictIds).stream()
                .collect(Collectors.toMap(WordDict::getId, Function.identity(), (a, b) -> a));
        return words.stream()
                .map(w -> toCard(w, dictMap.get(w.getDictId()), null, null))
                .collect(Collectors.toList());
    }

    // ==================== 词卡详情 ====================

    @Override
    public WordCardVO getWordCardVO(long userWordId, User loginUser) {
        UserWord userWord = getOwnedWord(userWordId, loginUser);
        WordDict dict = wordDictService.getById(userWord.getDictId());

        List<UserWordSighting> sightings = sightingMapper.selectListByQuery(QueryWrapper.create()
                .eq(UserWordSighting::getUserWordId, userWordId)
                .orderBy(UserWordSighting::getCreateTime, false));
        List<WordSightingVO> sightingVOs = sightings.stream().map(WordSightingVO::of).collect(Collectors.toList());
        return toCard(userWord, dict, null, sightingVOs);
    }

    // ==================== 笔记 ====================

    @Override
    public boolean updateNote(long userWordId, String note, User loginUser) {
        getOwnedWord(userWordId, loginUser);
        ThrowUtils.throwIf(note != null && note.length() > WordConstant.MAX_NOTE_LENGTH,
                ErrorCode.PARAMS_ERROR, "笔记过长");
        return UpdateChain.of(UserWord.class)
                .set(UserWord::getNote, note)
                .set(UserWord::getUpdateTime, LocalDateTime.now())
                .where(UserWord::getId).eq(userWordId)
                .update();
    }

    // ==================== 删除 ====================

    @Override
    public boolean deleteWord(long userWordId, User loginUser) {
        getOwnedWord(userWordId, loginUser);
        boolean removed = this.removeById(userWordId);
        dueQueue.remove(loginUser.getId(), userWordId);
        return removed;
    }

    // ==================== 统计 ====================

    @Override
    public WordStatisticsVO statistics(User loginUser) {
        long userId = loginUser.getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();

        WordStatisticsVO vo = new WordStatisticsVO();
        vo.setTotalCount(this.count(QueryWrapper.create().eq(UserWord::getUserId, userId)));
        vo.setTodayNewCount(this.count(QueryWrapper.create()
                .eq(UserWord::getUserId, userId)
                .ge(UserWord::getCreateTime, startOfToday)));
        vo.setDueCount(this.count(QueryWrapper.create()
                .eq(UserWord::getUserId, userId)
                .lt(UserWord::getMastery, MasteryEnum.MASTERED.getValue())
                .le(UserWord::getDueTime, now)));
        vo.setMasteredCount(this.count(QueryWrapper.create()
                .eq(UserWord::getUserId, userId)
                .eq(UserWord::getMastery, MasteryEnum.MASTERED.getValue())));
        vo.setStreakDays(calcStreak(userId, now.toLocalDate()));
        return vo;
    }

    /**
     * 连续打卡天数：以词卡的复习时间为准，从今天（或昨天）往前数连续有复习的天数
     */
    private long calcStreak(long userId, LocalDate today) {
        List<UserWord> reviewed = this.list(QueryWrapper.create()
                .select(UserWord::getLastReviewTime)
                .eq(UserWord::getUserId, userId)
                .isNotNull(UserWord::getLastReviewTime)
                .orderBy(UserWord::getLastReviewTime, false)
                .limit(2000));
        Set<LocalDate> days = reviewed.stream()
                .map(w -> w.getLastReviewTime().toLocalDate())
                .collect(Collectors.toCollection(TreeSet::new));
        if (days.isEmpty()) {
            return 0;
        }
        // 今天没复习就从昨天起算，允许「今天还没开始」不断签
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        long streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    // ==================== 通用 ====================

    /**
     * 取属于当前用户的词卡，越权 / 不存在直接抛
     */
    private UserWord getOwnedWord(long userWordId, User loginUser) {
        UserWord userWord = this.getById(userWordId);
        ThrowUtils.throwIf(userWord == null, ErrorCode.NOT_FOUND_ERROR, "词卡不存在");
        ThrowUtils.throwIf(!Objects.equals(userWord.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        return userWord;
    }

    /**
     * UserWord + WordDict → WordCardVO
     */
    private WordCardVO toCard(UserWord w, WordDict dict, Boolean newlyAdded, List<WordSightingVO> sightings) {
        WordCardVO vo = new WordCardVO();
        vo.setUserWordId(w.getId());
        vo.setDictId(w.getDictId());
        vo.setSpelling(w.getSpelling());
        vo.setMastery(w.getMastery());
        vo.setEncounterNum(w.getEncounterNum());
        vo.setReviewCount(w.getReviewCount());
        vo.setDueTime(w.getDueTime());
        vo.setNote(w.getNote());
        vo.setCreateTime(w.getCreateTime());
        vo.setEaseFactor(w.getEaseFactor());
        vo.setNewlyAdded(newlyAdded);
        vo.setSightings(sightings);
        if (dict != null) {
            vo.setPhonetic(dict.getPhonetic());
            vo.setTranslation(dict.getTranslation());
            vo.setDefinition(dict.getDefinition());
            vo.setPos(dict.getPos());
            vo.setExchange(dict.getExchange());
            vo.setTag(dict.getTag());
            vo.setSource(dict.getSource());
        }
        return vo;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
