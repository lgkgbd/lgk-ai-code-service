package com.lgk.lgkaicodeservice.model.enums;

import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 词书类型枚举，对应 word_book.type
 * <p>
 * 生词本不是特例，只是 type=0 的一条记录；两类词书的差异只在入池方式：
 * 生词本 push（录入即入池，dailyNewLimit=-1），预置词书 pull（每日按 dailyNewLimit 投放）
 */
public enum WordBookTypeEnum {

    PERSONAL("个人生词本", 0),
    SYSTEM("系统预置", 1);

    private final String text;
    private final Integer value;

    WordBookTypeEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    public static WordBookTypeEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value) || value < 0) {
            return null;
        }
        for (WordBookTypeEnum anEnum : WordBookTypeEnum.values()) {
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
