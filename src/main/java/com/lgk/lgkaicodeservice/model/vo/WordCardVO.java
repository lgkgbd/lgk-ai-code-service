package com.lgk.lgkaicodeservice.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 词卡视图（列表项 / 详情共用）
 * <p>
 * 融合了客观词条（word_dict）与我的学习状态（user_word）。详情页额外带 {@link #sightings}。
 */
@Data
public class WordCardVO implements Serializable {

    // ---- 学习状态（user_word）----

    /** 学习记录 id（user_word.id），前端操作用这个 */
    private Long userWordId;

    /** 词条 id（word_dict.id） */
    private Long dictId;

    /** 单词 */
    private String spelling;

    /** 掌握阶段 0=新词 1=学习中 2=复习中 3=已掌握 */
    private Integer mastery;

    /** 累计遇见次数 */
    private Integer encounterNum;

    /** 累计复习次数 */
    private Integer reviewCount;

    /** 下次复习时间 */
    private LocalDateTime dueTime;

    /** 私人笔记 */
    private String note;

    /** 首次录入时间 */
    private LocalDateTime createTime;

    // ---- 客观词条（word_dict）----

    /** 音标（可能为空，前端需优雅处理） */
    private String phonetic;

    /** 中文释义 */
    private String translation;

    /** 英文释义 */
    private String definition;

    /** 词性占比 */
    private String pos;

    /** 词形变化 */
    private String exchange;

    /** 考试标签 cet4/cet6/... */
    private String tag;

    /** 数据来源 ecdict/api/ai/manual */
    private String source;

    // ---- 本次录入结果标记（仅 capture 返回时有意义）----

    /**
     * 本次录入是否为新增。false 表示这个词之前就录过（第 N 次遇见），前端据此弹「第 N 次遇见 👀」
     */
    private Boolean newlyAdded;

    /** 详情页带上全部遇见记录，列表页为 null */
    private List<WordSightingVO> sightings;

    /** SM-2 难度系数（调试/展示用） */
    private BigDecimal easeFactor;

    private static final long serialVersionUID = 1L;
}
