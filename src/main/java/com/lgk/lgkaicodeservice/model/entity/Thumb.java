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
 * 点赞表 实体类。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("thumb")
public class Thumb implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 点赞id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 点赞类型
     */
    private Integer type;

    /**
     * 目标id
     */
    @Column("targetId")
    private Long targetId;

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

}
