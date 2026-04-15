package com.lgk.lgkaicodeservice.model.dto.thumb;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 临时点赞记录 DTO
 * 用于 Redis 临时存储，后续批量同步到数据库
 */
@Data
public class TempThumbDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 点赞记录 ID（数据库主键，取消点赞时可能为 null）
     */
    private Long thumbId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 点赞类型（1-帖子）
     */
    private Integer type;

    /**
     * 目标 ID（帖子 ID）
     */
    private Long targetId;

    /**
     * 操作类型：1-点赞，0-取消点赞
     */
    private Integer operation;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 时间片（用于分组）
     */
    private String timeSlice;

}
