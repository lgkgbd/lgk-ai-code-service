package com.lgk.lgkaicodeservice.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 帖子表 实体类。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("post")
public class Post implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 帖子id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表（json 数组）
     */
    private String tags;

    /**
     * 封面图片URL
     */
    @Column("coverImage")
    private String coverImage;

    /**
     * 点赞数
     */
    @Column("thumbNum")
    private Integer thumbNum;

    /**
     * 收藏数
     */
    @Column("favourNum")
    private Integer favourNum;

    /**
     * 浏览量
     */
    @Column("viewNum")
    private Integer viewNum;

    /**
     * 创建用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

    /**
     * 优先级
     */
    @Column("priority")
    private Integer priority;

}
