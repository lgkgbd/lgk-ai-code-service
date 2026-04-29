package com.lgk.lgkaicodeservice.model.enums;

import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum CommentTargetTypeEnum {

    POST("帖子", 0);

    private final String text;
    private final Integer value;

    CommentTargetTypeEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    public static CommentTargetTypeEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value) || value < 0) {
            return null;
        }
        for (CommentTargetTypeEnum anEnum : CommentTargetTypeEnum.values()) {
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
