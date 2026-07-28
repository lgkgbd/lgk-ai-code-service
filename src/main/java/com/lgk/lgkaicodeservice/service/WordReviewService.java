package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.WordReviewCardVO;

import java.util.List;

/**
 * 复习服务：统一队列 + 简化 SM-2 作答
 */
public interface WordReviewService {

    /**
     * 今日复习队列：取 dueTime &le; now 的词。
     * 优先 Redis ZSet（O(logN)），Redis 无数据时用 idx_user_due 回源 DB 并重建缓存。
     */
    List<WordReviewCardVO> getTodayQueue(User loginUser);

    /**
     * 提交作答
     * <ol>
     *   <li>校验 userWordId 属于当前用户，否则 NO_AUTH_ERROR</li>
     *   <li>Sm2Calculator 算新状态 → 更新 user_word → 写 user_word_review_log</li>
     *   <li>更新 Redis ZSet score；quality=0 则 dueTime=今天重新入队</li>
     *   <li>canGraduate 时 mastery=3 已掌握并移出复习池</li>
     * </ol>
     *
     * @param userWordId 学习记录 id
     * @param quality    作答质量 0-3
     * @param costMs     作答耗时(ms)，可为 null
     * @param loginUser  当前用户
     * @return 本次作答后的新掌握阶段
     */
    Integer submit(long userWordId, Integer quality, Integer costMs, User loginUser);

    /**
     * 待复习数（导航角标）
     */
    long countDue(User loginUser);
}
