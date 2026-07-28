package com.lgk.lgkaicodeservice.model.enums;

import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 单词掌握阶段枚举，对应 user_word.mastery
 */
public enum MasteryEnum {

    NEW("新词", 0),
    LEARNING("学习中", 1),
    REVIEWING("复习中", 2),
    MASTERED("已掌握", 3);

    private final String text;
    private final Integer value;

    MasteryEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    public static MasteryEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value) || value < 0) {
            return null;
        }
        for (MasteryEnum anEnum : MasteryEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
