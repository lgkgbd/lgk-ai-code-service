package com.lgk.lgkaicodeservice.service.word;

import com.lgk.lgkaicodeservice.constant.RedisConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;

/**
 * 待复习队列（Redis ZSet）封装
 * <p>
 * key = {@code word:due:{userId}}，member = userWordId，score = dueTime 的毫秒时间戳。
 * 取当日待复习即 {@code ZRANGEBYSCORE 0 now}，O(logN)。
 * <p>
 * 所有操作对 Redis 故障保持静默降级：写失败只记日志（DB 是最终真相，可用 idx_user_due 回源），
 * 绝不让缓存问题冒泡阻断录入/复习主流程。
 */
@Slf4j
@Component
public class WordDueQueue {

    @Resource
    private RedissonClient redissonClient;

    private RScoredSortedSet<Long> queueOf(long userId) {
        return redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(userId));
    }

    /**
     * dueTime → score（毫秒时间戳）
     */
    public static double toScore(LocalDateTime dueTime) {
        return dueTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 加入 / 更新某个词的到期时间
     */
    public void upsert(long userId, long userWordId, LocalDateTime dueTime) {
        if (dueTime == null) {
            return;
        }
        try {
            queueOf(userId).add(toScore(dueTime), userWordId);
        } catch (Exception e) {
            log.warn("写入待复习队列失败，userId={}, userWordId={}", userId, userWordId, e);
        }
    }

    /**
     * 移出队列（已掌握 / 已删除）
     */
    public void remove(long userId, long userWordId) {
        try {
            queueOf(userId).remove(userWordId);
        } catch (Exception e) {
            log.warn("移出待复习队列失败，userId={}, userWordId={}", userId, userWordId, e);
        }
    }

    /**
     * 取 score &le; now 的到期成员。Redis 故障时返回 null，交由上层回源 DB
     *
     * @return 到期的 userWordId 集合；Redis 不可用时返回 null（区别于「查得到但为空」）
     */
    public Collection<Long> pollDue(long userId, LocalDateTime now) {
        try {
            return queueOf(userId).valueRange(0, true, toScore(now), true);
        } catch (Exception e) {
            log.warn("读取待复习队列失败，回源 DB，userId={}", userId, e);
            return null;
        }
    }

    /**
     * 到期数量（导航角标）。Redis 故障时返回 -1，交由上层回源 DB
     */
    public long countDue(long userId, LocalDateTime now) {
        try {
            return queueOf(userId).count(0, true, toScore(now), true);
        } catch (Exception e) {
            log.warn("统计待复习数失败，回源 DB，userId={}", userId, e);
            return -1;
        }
    }

    /**
     * 批量重建队列（Redis 回源 DB 后回填缓存）
     */
    public void rebuild(long userId, java.util.Map<Long, LocalDateTime> dueMap) {
        if (dueMap == null || dueMap.isEmpty()) {
            return;
        }
        try {
            RScoredSortedSet<Long> queue = queueOf(userId);
            dueMap.forEach((userWordId, dueTime) -> {
                if (dueTime != null) {
                    queue.add(toScore(dueTime), userWordId);
                }
            });
        } catch (Exception e) {
            log.warn("重建待复习队列失败，userId={}", userId, e);
        }
    }
}
