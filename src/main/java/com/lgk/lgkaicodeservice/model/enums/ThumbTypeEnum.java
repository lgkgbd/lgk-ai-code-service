package com.lgk.lgkaicodeservice.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 点赞类型枚举
 * <p>
 * 用于区分不同类型内容的点赞，每种类型在 Redis 中有独立的 Key 命名空间：
 * <ul>
 *   <li>独立的用户点赞 Hash：thumb:user:{userId}:{type}</li>
 *   <li>独立的存在性 Set：thumb:exists:{type}</li>
 *   <li>独立的计数 Key：thumb:count:{type}:{targetId}</li>
 * </ul>
 * <p>
 * 扩展方式：新增枚举值 + 对应的 Handler 实现即可
 */
public enum ThumbTypeEnum {

    POST("post", 0, "帖子"),
    IMAGE("image", 1, "图片"),
    VIDEO("video", 2, "视频"),
    COMMENT("comment", 3, "评论");

    private final String value;
    private final Integer code;
    private final String text;

    ThumbTypeEnum(String value, Integer code, String text) {
        this.value = value;
        this.code = code;
        this.text = text;
    }

    /**
     * 获取值列表（用于数据库存储）
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.code).collect(Collectors.toList());
    }

    /**
     * 根据 code 获取枚举
     */
    public static ThumbTypeEnum getEnumByCode(Integer code) {
        if (ObjectUtils.isEmpty(code) || code < 0) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据字符串 value 获取枚举
     */
    public static ThumbTypeEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取 Redis Key 中使用的值（字符串形式，更易读）
     */
    public String getValue() {
        return value;
    }

    /**
     * 获取枚举对应的整数值（用于数据库存储）
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取类型描述
     */
    public String getText() {
        return text;
    }
}
