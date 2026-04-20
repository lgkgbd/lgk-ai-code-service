package com.lgk.lgkaicodeservice.constant;

/**
 * 点赞相关常量
 * <p>
 * Key 设计原则（通用化，支持多类型扩展）：
 * <ul>
 *   <li>thumb:user:{userId}:{type}           → 用户点赞 Hash，field=targetId，value=thumbId</li>
 *   <li>thumb:exists:{type}                  → 目标存在性 Set，用于快速校验</li>
 *   <li>thumb:count:{type}:{targetId}        → 点赞计数 String，支持原子增减</li>
 *   <li>thumb:temp:{timeSlice}               → 临时点赞记录，等待批量同步</li>
 *   <li>thumb:temp:queue                     → 待同步任务队列</li>
 *   <li>thumb:lock:user:{userId}             → 用户操作分布式锁</li>
 * </ul>
 * <p>
 * 目前支持的类型（ThumbTypeEnum）：
 * <ul>
 *   <li>post   → 帖子点赞</li>
 *   <li>后续可扩展：image(图片), video(视频), comment(评论) 等</li>
 * </ul>
 */
public interface ThumbConstant {

    // ==================== Key 前缀 ====================

    /**
     * 用户点赞记录 Key 前缀
     * 格式: thumb:user:{userId}:{type}
     * 示例: thumb:user:123:post
     */
    String USER_THUMB_KEY_PREFIX = "thumb:user:";

    /**
     * 目标存在性验证 Key 前缀（按类型分开）
     * 格式: thumb:exists:{type}
     * 示例: thumb:exists:post, thumb:exists:image
     */
    String EXISTS_KEY_PREFIX = "thumb:exists:";

    /**
     * 点赞计数 Key 前缀（按类型和目标分开）
     * 格式: thumb:count:{type}:{targetId}
     * 示例: thumb:count:post:456
     */
    String COUNT_KEY_PREFIX = "thumb:count:";

    /**
     * 临时点赞记录 Key 前缀
     * 格式: thumb:temp:{timeSlice}
     * 用于批量同步到数据库前的临时存储
     */
    String TEMP_THUMB_KEY_PREFIX = "thumb:temp:";

    /**
     * 待同步点赞队列 Key
     * 存储所有需要同步到数据库的临时点赞记录 Key
     */
    String TEMP_THUMB_QUEUE_KEY = "thumb:temp:queue";

    /**
     * 用户操作分布式锁 Key 前缀
     * 格式: thumb:lock:user:{userId}
     */
    String USER_LOCK_KEY_PREFIX = "thumb:lock:user:";

    // ==================== 业务常量 ====================

    /**
     * 批量同步时间间隔（秒）—— 降级路径（TempThumbStorageService）专用
     */
    int BATCH_SYNC_INTERVAL_SECONDS = 10;

    /**
     * 批量同步每批次数量—— 降级路径（TempThumbStorageService）专用
     */
    int BATCH_SYNC_SIZE = 100;

    // ==================== MQ 批量攒批常量 ====================

    /**
     * MQ 消费端内存攒批——每批次最大数量
     * 攒够此数量时，即使定时任务没到也会在下次 flush 时全部取出处理
     */
    int MQ_BATCH_SIZE = 100;

    /**
     * MQ 消费端内存攒批——定时刷新间隔（毫秒）
     * 每隔 1 秒检查一次内存队列，有数据就批量刷写到数据库
     * 低流量时消息最多延迟 1 秒，不会积压
     */
    long MQ_FLUSH_INTERVAL_MS = 1000L;

}
