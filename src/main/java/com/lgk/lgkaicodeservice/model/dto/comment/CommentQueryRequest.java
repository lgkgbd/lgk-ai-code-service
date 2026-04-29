package com.lgk.lgkaicodeservice.model.dto.comment;

import com.lgk.lgkaicodeservice.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class CommentQueryRequest extends PageRequest implements Serializable {

    /** 目标类型，0=帖子 */
    private Integer targetType;

    /** 目标ID */
    private Long targetId;

    /**
     * 父评论ID。
     * 查顶级评论时不传（或传0），查某条评论的回复时传入。
     */
    private Long parentId;

    private static final long serialVersionUID = 1L;
}
