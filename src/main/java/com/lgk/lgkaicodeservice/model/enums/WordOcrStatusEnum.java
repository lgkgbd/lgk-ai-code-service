package com.lgk.lgkaicodeservice.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 拍照识别任务状态
 * <p>
 * 状态机（只进不退）：
 * <pre>
 *   PENDING ──► RUNNING ──► SUCCEED
 *                  │
 *                  └──────► FAILED
 * </pre>
 * 前端轮询到 SUCCEED / FAILED 即为终态，停止轮询。
 */
@Getter
public enum WordOcrStatusEnum {

    /**
     * 已入队，等待 worker 线程调度
     */
    PENDING("排队中", "pending"),

    /**
     * 识别中，progress 会随已完成图片数递增
     */
    RUNNING("识别中", "running"),

    /**
     * 识别完成，items 为待用户确认的候选词
     */
    SUCCEED("识别完成", "succeed"),

    /**
     * 识别失败，errorMsg 为原因
     */
    FAILED("识别失败", "failed");

    private final String text;

    private final String value;

    WordOcrStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值；无匹配返回 null
     */
    public static WordOcrStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (WordOcrStatusEnum anEnum : WordOcrStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 是否终态（前端可停止轮询）
     */
    public boolean isFinal() {
        return this == SUCCEED || this == FAILED;
    }
}
