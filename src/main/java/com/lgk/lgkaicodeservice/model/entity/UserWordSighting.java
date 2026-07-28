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
 * 单词遇见记录表（上下文快照）
 * <p>
 * 本功能区别于普通背单词 App 的核心资产：一个词可以有多条 sighting，
 * 复习时优先用 sentence 挖空出题，而不是干巴巴的英译中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_word_sighting")
public class UserWordSighting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 学习记录id */
    @Column("userWordId")
    private Long userWordId;

    /** 用户id（冗余，便于按用户清理/统计） */
    @Column("userId")
    private Long userId;

    /** 遇见时的原句 */
    private String sentence;

    /** 来源标题（页面标题/书名/播客名） */
    @Column("sourceTitle")
    private String sourceTitle;

    /** 来源链接 */
    @Column("sourceUrl")
    private String sourceUrl;

    /** 录入渠道 manual/paste/bookmarklet/extension */
    private String channel;

    /** 遇见时间 */
    @Column("createTime")
    private LocalDateTime createTime;
}
