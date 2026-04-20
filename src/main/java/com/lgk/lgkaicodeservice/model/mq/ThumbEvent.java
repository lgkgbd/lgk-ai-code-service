package com.lgk.lgkaicodeservice.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点赞事件消息体
 * <p>
 * 用于 RabbitMQ 消息传递，携带点赞/取消点赞的完整信息。
 * 消费端可转换为 TempThumbDTO 复用的 ThumbBatchSyncJob 的处理逻辑。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThumbEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一 ID（用于幂等去重）
     */
    private String msgId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 点赞类型（对应 ThumbTypeEnum.getCode()）
     */
    private Integer type;

    /**
     * 目标 ID（如帖子 ID）
     */
    private Long targetId;

    /**
     * 点赞记录 ID（数据库主键，取消点赞时可能为 null）
     */
    private Long thumbId;

    /**
     * 操作类型：1-点赞，0-取消点赞
     */
    private Integer operation;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
