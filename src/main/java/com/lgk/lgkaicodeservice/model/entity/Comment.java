package com.lgk.lgkaicodeservice.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("comment")
public class Comment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String content;

    /** 图片URL列表，JSON数组存储 */
    private String images;

    @Column("targetType")
    private Integer targetType;

    @Column("targetId")
    private Long targetId;

    @Column("userId")
    private Long userId;

    /** 父评论ID，0为顶级评论 */
    @Column("parentId")
    private Long parentId;

    /** 根评论ID，0为顶级评论；回复的回复指向同一根评论，用于两级展示 */
    @Column("rootId")
    private Long rootId;

    /** 被回复的用户ID，顶级评论为0 */
    @Column("replyToUserId")
    private Long replyToUserId;

    /** 被回复内容摘要（快照，避免查父评论） */
    @Column("replyToContent")
    private String replyToContent;

    @Column("thumbNum")
    private Integer thumbNum;

    @Column("replyNum")
    private Integer replyNum;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
