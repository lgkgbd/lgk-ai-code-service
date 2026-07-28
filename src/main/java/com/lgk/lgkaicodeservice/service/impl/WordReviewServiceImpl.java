package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.mapper.UserWordReviewLogMapper;
import com.lgk.lgkaicodeservice.mapper.UserWordSightingMapper;
import com.lgk.lgkaicodeservice.model.entity.*;
import com.lgk.lgkaicodeservice.model.enums.MasteryEnum;
import com.lgk.lgkaicodeservice.model.enums.ReviewQualityEnum;
import com.lgk.lgkaicodeservice.model.vo.WordReviewCardVO;
import com.lgk.lgkaicodeservice.service.WordDictService;
import com.lgk.lgkaicodeservice.service.WordReviewService;
import com.lgk.lgkaicodeservice.service.WordService;
import com.lgk.lgkaicodeservice.service.word.WordDueQueue;
import com.lgk.lgkaicodeservice.utils.Sm2Calculator;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 复习服务实现
 */
@Slf4j
@Service
public class WordReviewServiceImpl implements WordReviewService {

    /** 单次拉取复习队列上限，避免一次性拉太多 */
    private static final int MAX_QUEUE_SIZE = 200;

    @Resource
    private WordService wordService;

    @Resource
    private WordDictService wordDictService;

    @Resource
    private UserWordSightingMapper sightingMapper;

    @Resource
    private UserWordReviewLogMapper reviewLogMapper;

    @Resource
    private WordDueQueue dueQueue;

    // ==================== 今日队列 ====================

    @Override
    public List<WordReviewCardVO> getTodayQueue(User loginUser) {
        long userId = loginUser.getId();
        LocalDateTime now = LocalDateTime.now();

        List<Long> dueIds;
        Collection<Long> fromRedis = dueQueue.pollDue(userId, now);
        if (fromRedis != null) {
            // Redis 命中（含「命中但为空」）
            dueIds = new ArrayList<>(fromRedis);
        } else {
            // Redis 不可用 → 回源 DB，并重建缓存
            dueIds = fallbackFromDbAndRebuild(userId, now);
        }
        if (CollUtil.isEmpty(dueIds)) {
            return new ArrayList<>();
        }
        if (dueIds.size() > MAX_QUEUE_SIZE) {
            dueIds = dueIds.subList(0, MAX_QUEUE_SIZE);
        }

        // 载入词卡（过滤掉已删除/已掌握的脏成员）
        List<UserWord> words = wordService.listByIds(dueIds).stream()
                .filter(w -> w.getMastery() == null || w.getMastery() < MasteryEnum.MASTERED.getValue())
                .collect(Collectors.toList());
        return buildReviewCards(words);
    }

    /**
     * Redis 失效兜底：idx_user_due 索引查 dueTime &le; now 未掌握的词，并回填 Redis
     */
    private List<Long> fallbackFromDbAndRebuild(long userId, LocalDateTime now) {
        List<UserWord> due = wordService.list(QueryWrapper.create()
                .eq(UserWord::getUserId, userId)
                .lt(UserWord::getMastery, MasteryEnum.MASTERED.getValue())
                .le(UserWord::getDueTime, now)
                .orderBy(UserWord::getDueTime, true)
                .limit(MAX_QUEUE_SIZE));
        if (CollUtil.isEmpty(due)) {
            return new ArrayList<>();
        }
        Map<Long, LocalDateTime> dueMap = due.stream()
                .collect(Collectors.toMap(UserWord::getId, UserWord::getDueTime, (a, b) -> a, LinkedHashMap::new));
        dueQueue.rebuild(userId, dueMap);
        return new ArrayList<>(dueMap.keySet());
    }

    /**
     * 组装复习卡：优先用最近一条遇见原句挖空
     */
    private List<WordReviewCardVO> buildReviewCards(List<UserWord> words) {
        if (CollUtil.isEmpty(words)) {
            return new ArrayList<>();
        }
        List<Long> dictIds = words.stream().map(UserWord::getDictId).distinct().collect(Collectors.toList());
        Map<Long, WordDict> dictMap = wordDictService.listByIds(dictIds).stream()
                .collect(Collectors.toMap(WordDict::getId, Function.identity(), (a, b) -> a));

        return words.stream().map(w -> {
            WordReviewCardVO vo = new WordReviewCardVO();
            vo.setUserWordId(w.getId());
            vo.setSpelling(w.getSpelling());
            WordDict dict = dictMap.get(w.getDictId());
            if (dict != null) {
                vo.setPhonetic(dict.getPhonetic());
                vo.setTranslation(dict.getTranslation());
                vo.setDefinition(dict.getDefinition());
                vo.setPos(dict.getPos());
            }
            // 最近一条 sighting 作正面挖空题干
            UserWordSighting latest = sightingMapper.selectOneByQuery(QueryWrapper.create()
                    .eq(UserWordSighting::getUserWordId, w.getId())
                    .orderBy(UserWordSighting::getCreateTime, false)
                    .limit(1));
            if (latest != null && StringUtils.hasText(latest.getSentence())) {
                vo.setClozeSentence(cloze(latest.getSentence(), w.getSpelling()));
                vo.setSourceTitle(latest.getSourceTitle());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 把原句里的目标词挖空成 ____（大小写不敏感，全词匹配）
     */
    static String cloze(String sentence, String spelling) {
        if (!StringUtils.hasText(sentence) || !StringUtils.hasText(spelling)) {
            return sentence;
        }
        return sentence.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(spelling) + "\\b", "＿＿＿＿");
    }

    // ==================== 提交作答 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer submit(long userWordId, Integer quality, Integer costMs, User loginUser) {
        ThrowUtils.throwIf(quality == null || ReviewQualityEnum.getEnumByValue(quality) == null,
                ErrorCode.PARAMS_ERROR, "作答质量非法");

        // 1. 校验归属
        UserWord userWord = wordService.getById(userWordId);
        ThrowUtils.throwIf(userWord == null, ErrorCode.NOT_FOUND_ERROR, "词卡不存在");
        ThrowUtils.throwIf(!Objects.equals(userWord.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        long userId = loginUser.getId();
        LocalDateTime now = LocalDateTime.now();

        // 2. 算新状态
        Sm2Calculator.Sm2Result result = Sm2Calculator.calculate(
                userWord.getEaseFactor(), userWord.getIntervalDays(), userWord.getReviewCount(), quality, now);

        boolean forgot = quality == ReviewQualityEnum.FORGOT.getValue();
        int newMastery = decideMastery(userWord.getMastery(), quality, result.isCanGraduate());

        // 3. 更新 user_word
        UpdateChain<UserWord> chain = UpdateChain.of(UserWord.class)
                .set(UserWord::getEaseFactor, result.getEaseFactor())
                .set(UserWord::getIntervalDays, result.getIntervalDays())
                .set(UserWord::getReviewCount, result.getReviewCount())
                .set(UserWord::getMastery, newMastery)
                .set(UserWord::getDueTime, result.getDueTime())
                .set(UserWord::getLastReviewTime, now)
                .set(UserWord::getUpdateTime, now);
        if (forgot) {
            chain.setRaw("lapseCount", "lapseCount + 1");
        }
        chain.where(UserWord::getId).eq(userWordId).update();

        // 4. 写复习日志
        reviewLogMapper.insert(UserWordReviewLog.builder()
                .userWordId(userWordId)
                .userId(userId)
                .quality(quality)
                .costMs(costMs == null ? 0 : costMs)
                .intervalDays(result.getIntervalDays())
                .easeFactor(result.getEaseFactor())
                .reviewTime(now)
                .build());

        // 5. 更新 Redis 队列：毕业则移出，否则按新 dueTime 重排（quality=0 的 dueTime=now，重新入队）
        if (newMastery == MasteryEnum.MASTERED.getValue()) {
            dueQueue.remove(userId, userWordId);
        } else {
            dueQueue.upsert(userId, userWordId, result.getDueTime());
        }
        return newMastery;
    }

    /**
     * 掌握阶段流转：
     * <ul>
     *   <li>可毕业 → 已掌握(3)</li>
     *   <li>答错(quality=0) → 回到学习中(1)</li>
     *   <li>其余答对 → 至少进到复习中(2)，不回退已有的更高阶段</li>
     * </ul>
     */
    private int decideMastery(Integer current, int quality, boolean canGraduate) {
        int cur = current == null ? MasteryEnum.NEW.getValue() : current;
        if (canGraduate) {
            return MasteryEnum.MASTERED.getValue();
        }
        if (quality == ReviewQualityEnum.FORGOT.getValue()) {
            return MasteryEnum.LEARNING.getValue();
        }
        return Math.max(cur, MasteryEnum.REVIEWING.getValue());
    }

    // ==================== 角标 ====================

    @Override
    public long countDue(User loginUser) {
        long userId = loginUser.getId();
        LocalDateTime now = LocalDateTime.now();
        long fromRedis = dueQueue.countDue(userId, now);
        if (fromRedis >= 0) {
            return fromRedis;
        }
        // Redis 不可用 → DB 兜底
        return wordService.count(QueryWrapper.create()
                .eq(UserWord::getUserId, userId)
                .lt(UserWord::getMastery, MasteryEnum.MASTERED.getValue())
                .le(UserWord::getDueTime, now));
    }
}
