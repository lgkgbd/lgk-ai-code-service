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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单词复习日志表
 * <p>
 * 有了它才能画「记忆曲线」和年度报告。数据量 = 用户数 × 词数 × 复习次数，二期需规划归档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_word_review_log")
public class UserWordReviewLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 学习记录id */
    @Column("userWordId")
    private Long userWordId;

    /** 用户id */
    @Column("userId")
    private Long userId;

    /** 本次作答质量 0=完全忘记 1=模糊 2=想起来了 3=秒答，见 ReviewQualityEnum */
    private Integer quality;

    /** 作答耗时(ms) */
    @Column("costMs")
    private Integer costMs;

    /** 本次复习后的新间隔 */
    @Column("intervalDays")
    private Integer intervalDays;

    /** 本次复习后的难度系数 */
    @Column("easeFactor")
    private BigDecimal easeFactor;

    /** 复习时间 */
    @Column("reviewTime")
    private LocalDateTime reviewTime;
}
