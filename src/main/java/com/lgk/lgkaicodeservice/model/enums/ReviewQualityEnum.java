package com.lgk.lgkaicodeservice.model.enums;

import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 复习作答质量枚举，对应 user_word_review_log.quality
 * <p>
 * 简化版 SM-2 只用四档，各档对间隔梯度的影响见 {@link com.lgk.lgkaicodeservice.utils.Sm2Calculator}
 */
public enum ReviewQualityEnum {

    FORGOT("完全忘记", 0),
    VAGUE("模糊", 1),
    RECALLED("想起来了", 2),
    INSTANT("秒答", 3);

    private final String text;
    private final Integer value;

    ReviewQualityEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    public static ReviewQualityEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value) || value < 0) {
            return null;
        }
        for (ReviewQualityEnum anEnum : ReviewQualityEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 是否算答对（想起来了 / 秒答）
     */
    public boolean isPassed() {
        return this.value >= RECALLED.value;
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
