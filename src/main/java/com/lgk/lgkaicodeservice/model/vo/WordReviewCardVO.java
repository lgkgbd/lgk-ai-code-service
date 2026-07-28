package com.lgk.lgkaicodeservice.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 复习卡片视图
 * <p>
 * 正面：优先用最近一条遇见原句挖空（sentence 里把 spelling 抠掉）；无原句时退回词典例句/纯英译中。
 * 背面：音标 + 释义。
 */
@Data
public class WordReviewCardVO implements Serializable {

    /** 学习记录 id（user_word.id），提交作答时回传 */
    private Long userWordId;

    /** 单词（背面答案） */
    private String spelling;

    /** 音标 */
    private String phonetic;

    /** 中文释义 */
    private String translation;

    /** 英文释义 */
    private String definition;

    /** 词性 */
    private String pos;

    /** 挖空后的原句（正面题干）；无原句时为 null，前端退回纯听写/英译中 */
    private String clozeSentence;

    /** 原句来源标题 */
    private String sourceTitle;

    private static final long serialVersionUID = 1L;
}
