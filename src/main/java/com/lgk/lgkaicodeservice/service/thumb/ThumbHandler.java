package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * 点赞处理器接口
 * <p>
 * 定义点赞操作的核心行为，每种内容类型（帖子、图片、视频等）需要实现此接口。
 * 通过 {@link ThumbHandlerFactory} 统一管理和获取对应类型的处理器。
 *
 * @see ThumbHandlerFactory
 * @see com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum
 */
public interface ThumbHandler {

    /**
     * 获取该处理器对应的点赞类型
     *
     * @return 点赞类型枚举
     */
    ThumbTypeEnum getType();

    /**
     * 校验目标是否存在（查询数据库）
     */
    boolean checkTargetExists(Long targetId);

    /**
     * 校验目标是否存在（查询 Redis）
     * 用于高性能场景，避免数据库查询
     */
    default boolean checkTargetExistsInRedis(Long targetId) {
        // 默认实现回退到数据库查询
        return checkTargetExists(targetId);
    }

    /**
     * 点赞 +1（更新数据库）
     */
    Integer incrementThumb(Long targetId);

    /**
     * 批量点赞（聚合写）
     *
     * @param targetCountMap targetId -> 点赞增量（多条合并为一次 UPDATE）
     */
    default void incrementThumbBatch(Map<Long, Long> targetCountMap) {
        // 默认实现：逐条调用（子类可覆盖为真正的批量聚合）
        for (Map.Entry<Long, Long> entry : targetCountMap.entrySet()) {
            Long targetId = entry.getKey();
            Long count = entry.getValue();
            for (int i = 0; i < count; i++) {
                incrementThumb(targetId);
            }
        }
    }

    /**
     * 取消点赞 -1（更新数据库）
     */
    Integer decrementThumb(Long targetId);

    /**
     * 批量取消点赞（聚合写）
     *
     * @param targetIds 目标 ID 列表
     */
    default void decrementThumbBatch(List<Long> targetIds) {
        for (Long targetId : targetIds) {
            decrementThumb(targetId);
        }
    }

    /**
     * 点赞 +1（仅更新 Redis，用于临时存储模式）
     */
    default Integer incrementThumbInRedis(Long targetId) {
        // 默认实现直接调用数据库版本，子类可以覆盖实现纯 Redis 版本
        return incrementThumb(targetId);
    }

    /**
     * 取消点赞 -1（仅更新 Redis，用于临时存储模式）
     */
    default Integer decrementThumbInRedis(Long targetId) {
        // 默认实现直接调用数据库版本，子类可以覆盖实现纯 Redis 版本
        return decrementThumb(targetId);
    }
}
