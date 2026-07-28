package com.lgk.lgkaicodeservice.model.dto.word;

import lombok.Data;

import java.io.Serializable;

/**
 * 复习作答提交请求
 */
@Data
public class WordReviewSubmitRequest implements Serializable {

    /**
     * 学习记录 id（user_word.id）
     */
    private Long userWordId;

    /**
     * 作答质量 0=完全忘记 1=模糊 2=想起来了 3=秒答，见 ReviewQualityEnum
     */
    private Integer quality;

    /**
     * 作答耗时(ms)，可选
     */
    private Integer costMs;

    private static final long serialVersionUID = 1L;
}
