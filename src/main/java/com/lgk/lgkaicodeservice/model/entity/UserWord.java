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
 * 用户单词学习状态表（一人一词唯一）★核心表★
 * <p>
 * unique key (userId, dictId) 是整个设计的地基：同一个词无论来自生词本还是六级词书，
 * 掌握度只有一份、复习曲线只有一条。因此这里没有 type / bookId 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_word")
public class UserWord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 用户id */
    @Column("userId")
    private Long userId;

    /** 词条id */
    @Column("dictId")
    private Long dictId;

    /** 单词（冗余，列表页免 join） */
    private String spelling;

    /** 掌握阶段 0=新词 1=学习中 2=复习中 3=已掌握，见 MasteryEnum */
    private Integer mastery;

    /** SM-2 难度系数，初始 2.50，下限 1.30 */
    @Column("easeFactor")
    private BigDecimal easeFactor;

    /** 当前复习间隔（天） */
    @Column("intervalDays")
    private Integer intervalDays;

    /** 累计复习次数 */
    @Column("reviewCount")
    private Integer reviewCount;

    /** 累计遗忘次数（答错） */
    @Column("lapseCount")
    private Integer lapseCount;

    /** 累计遇见次数（重复录入会 +1） */
    @Column("encounterNum")
    private Integer encounterNum;

    /** 下次复习时间，同时写入 Redis ZSet 做当日队列 */
    @Column("dueTime")
    private LocalDateTime dueTime;

    /** 上次复习时间 */
    @Column("lastReviewTime")
    private LocalDateTime lastReviewTime;

    /** 私人笔记 */
    private String note;

    /** 首次录入时间 */
    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
