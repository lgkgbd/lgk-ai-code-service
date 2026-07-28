package com.lgk.lgkaicodeservice.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 词条数据来源枚举，对应 word_dict.source
 * <p>
 * 补全责任链按 order 从小到大依次尝试，命中即写入对应 source，日后可按来源批量重刷。
 */
@Getter
public enum WordSourceEnum {

    ECDICT("本地词库", "ecdict", 0),
    API("免费词典接口", "api", 10),
    AI("大模型兜底", "ai", 100),
    MANUAL("人工占位", "manual", 1000);

    private final String text;

    private final String value;

    /**
     * 补全责任链优先级，越小越先尝试
     */
    private final int order;

    WordSourceEnum(String text, String value, int order) {
        this.text = text;
        this.value = value;
        this.order = order;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static WordSourceEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (WordSourceEnum anEnum : WordSourceEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
