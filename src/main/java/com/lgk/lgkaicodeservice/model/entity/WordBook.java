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

/**
 * 词书表
 * <p>
 * 生词本不是特例，它就是 type=0 的一条记录，接口和代码路径完全共用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("word_book")
public class WordBook implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 词书名称 */
    private String name;

    /** 词书简介 */
    private String description;

    /** 封面图 URL */
    @Column("coverImage")
    private String coverImage;

    /** 类型 0=个人生词本 1=系统预置词书，见 WordBookTypeEnum */
    private Integer type;

    /** 语言 */
    private String lang;

    /** 归属用户id，0=系统词书 */
    @Column("ownerId")
    private Long ownerId;

    /** 词书词数（冗余，避免 count） */
    @Column("wordCount")
    private Integer wordCount;

    /** 是否公开 0/1 */
    @Column("isPublic")
    private Integer isPublic;

    /** 展示排序，越小越前 */
    @Column("sortOrder")
    private Integer sortOrder;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
