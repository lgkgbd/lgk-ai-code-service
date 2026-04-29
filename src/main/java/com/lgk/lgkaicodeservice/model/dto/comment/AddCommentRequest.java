package com.lgk.lgkaicodeservice.model.dto.comment;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AddCommentRequest implements Serializable {

    /** 评论内容 */
    private String content;

    /** 图片URL列表 */
    private List<String> images;

    /** 目标类型，0=帖子 */
    private Integer targetType;

    /** 目标ID */
    private Long targetId;

    /**
     * 父评论ID。
     * 发顶级评论时不传（或传0），回复时传父评论ID。
     */
    private Long parentId;

    /**
     * 被回复的用户ID。
     * 回复他人时传入，用于展示"回复 @xxx"。
     */
    private Long replyToUserId;

    private static final long serialVersionUID = 1L;
}
