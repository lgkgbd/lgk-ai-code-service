package com.lgk.lgkaicodeservice.utils;

import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;

/**
 * Redis Key 工具类
 * <p>
 * 所有 Key 格式统一管理，便于维护和扩展。
 * 设计原则：类型驱动，每种类型独立命名空间。
 */
public class RedisKeyUtil {

    // ==================== 用户点赞相关 ====================

    /**
     * 获取用户点赞记录 Key
     * 格式: thumb:user:{userId}:{type}
     * 示例: thumb:user:123:post
     *
     * @param userId 用户ID
     * @param type   点赞类型
     * @return Redis Key
     */
    public static String getUserThumbKey(Long userId, ThumbTypeEnum type) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId + ":" + type.getValue();
    }

    /**
     * 获取用户所有类型点赞的 Key（用于清理等场景）
     * 格式: thumb:user:{userId}:*
     */
    public static String getUserThumbKeyPattern(Long userId) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId + ":*";
    }

    // ==================== 存在性校验相关 ====================

    /**
     * 获取目标存在性 Set 的 Key
     * 格式: thumb:exists:{type}
     * 示例: thumb:exists:post, thumb:exists:image
     *
     * @param type 点赞类型
     * @return Redis Key
     */
    public static String getExistsKey(ThumbTypeEnum type) {
        return ThumbConstant.EXISTS_KEY_PREFIX + type.getValue();
    }

    // ==================== 计数相关 ====================

    /**
     * 获取目标点赞计数 Key
     * 格式: thumb:count:{type}:{targetId}
     * 示例: thumb:count:post:456
     *
     * @param type     点赞类型
     * @param targetId 目标ID
     * @return Redis Key
     */
    public static String getCountKey(ThumbTypeEnum type, Long targetId) {
        return ThumbConstant.COUNT_KEY_PREFIX + type.getValue() + ":" + targetId;
    }

    // ==================== 临时存储相关 ====================

    /**
     * 获取临时点赞记录 Key
     * 格式: thumb:temp:{timeSlice}
     *
     * @param timeSlice 时间片标识
     * @return Redis Key
     */
    public static String getTempThumbKey(String timeSlice) {
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX + timeSlice;
    }

    // ==================== 锁相关 ====================

    /**
     * 获取用户操作分布式锁 Key
     * 格式: thumb:lock:user:{userId}
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String getUserLockKey(Long userId) {
        return ThumbConstant.USER_LOCK_KEY_PREFIX + userId;
    }

}
