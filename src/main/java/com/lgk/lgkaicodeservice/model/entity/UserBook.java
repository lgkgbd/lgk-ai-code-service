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
 * 用户订阅词书表
 * <p>
 * 两类词书的唯一差异就在 dailyNewLimit / learnCursor 上：
 * 生词本 dailyNewLimit = -1 录入即入池（push）；预置词书每天从 learnCursor 往后取若干新词（pull）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_book")
public class UserBook implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 用户id */
    @Column("userId")
    private Long userId;

    /** 词书id */
    @Column("bookId")
    private Long bookId;

    /** 每日新词投放量，-1=不限（生词本） */
    @Column("dailyNewLimit")
    private Integer dailyNewLimit;

    /** 已投放到词书的第几个词（pull 类词书用）。不叫 cursor 是因为它是 MySQL 保留字 */
    @Column("learnCursor")
    private Integer learnCursor;

    /** 状态 0=学习中 1=已暂停 2=已学完 */
    private Integer status;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;
}
