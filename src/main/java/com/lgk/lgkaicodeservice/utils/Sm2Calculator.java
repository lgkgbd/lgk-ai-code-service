package com.lgk.lgkaicodeservice.utils;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.model.enums.ReviewQualityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 简化版 SM-2 复习间隔计算
 * <p>
 * 与标准 SM-2 的区别：间隔不由 {@code interval * easeFactor} 连乘得出，而是走一条固定的
 * 间隔梯度 {@code 1 / 2 / 4 / 7 / 15 / 30 / 60} 天，按作答质量在梯度上进退级。
 * easeFactor 字段照常维护并落库，二期换标准 SM-2 或 FSRS 时不需要改表。
 * <p>
 * 四档作答质量的规则：
 * <table border="1">
 *   <caption>quality → 梯度变化 / easeFactor 变化</caption>
 *   <tr><th>quality</th><th>含义</th><th>梯度</th><th>easeFactor</th></tr>
 *   <tr><td>0</td><td>完全忘记</td><td>归零级（1 天），且今天重来</td><td>-0.20</td></tr>
 *   <tr><td>1</td><td>模糊</td><td>退一级</td><td>-0.10</td></tr>
 *   <tr><td>2</td><td>想起来了</td><td>进一级</td><td>不变</td></tr>
 *   <tr><td>3</td><td>秒答</td><td>进两级</td><td>+0.10</td></tr>
 * </table>
 * <p>
 * easeFactor 钳制在 [1.30, 2.80]，下限必须守住，否则连续答错会把它击穿成负数。
 *
 * @see WordConstant#REVIEW_INTERVAL_GRADIENT
 */
public class Sm2Calculator {

    private Sm2Calculator() {
    }

    private static final int[] GRADIENT = WordConstant.REVIEW_INTERVAL_GRADIENT;

    private static final int MAX_LEVEL = GRADIENT.length - 1;

    /**
     * 尚未进入梯度（新词，intervalDays = 0）时的虚拟级别，进一级正好落到 1 天
     */
    private static final int LEVEL_NOT_STARTED = -1;

    /**
     * 计算下一次复习安排
     *
     * @param easeFactor   当前难度系数，null 视为初始值 2.50
     * @param intervalDays 当前复习间隔（天），新词传 0
     * @param reviewCount  当前累计复习次数
     * @param quality      本次作答质量 0-3，见 {@link ReviewQualityEnum}
     * @return 计算结果，含新的 easeFactor / intervalDays / dueTime / canGraduate
     * @throws IllegalArgumentException quality 不在 0-3 范围内
     */
    public static Sm2Result calculate(BigDecimal easeFactor, Integer intervalDays,
                                      Integer reviewCount, Integer quality) {
        return calculate(easeFactor, intervalDays, reviewCount, quality, LocalDateTime.now());
    }

    /**
     * 计算下一次复习安排（可注入当前时间，便于单测断言 dueTime）
     *
     * @param now 当前时间
     */
    public static Sm2Result calculate(BigDecimal easeFactor, Integer intervalDays,
                                      Integer reviewCount, Integer quality, LocalDateTime now) {
        ReviewQualityEnum qualityEnum = ReviewQualityEnum.getEnumByValue(quality);
        if (qualityEnum == null) {
            throw new IllegalArgumentException("非法的作答质量 quality = " + quality);
        }

        int currentLevel = levelOf(intervalDays);
        BigDecimal currentEase = easeFactor == null ? WordConstant.DEFAULT_EASE_FACTOR : easeFactor;

        int newLevel;
        BigDecimal newEase;
        switch (qualityEnum) {
            case FORGOT -> {
                // 完全忘记：直接回到梯度起点，今天重来
                newLevel = 0;
                newEase = currentEase.subtract(WordConstant.EASE_PENALTY_FORGOT);
            }
            case VAGUE -> {
                newLevel = currentLevel - 1;
                newEase = currentEase.subtract(WordConstant.EASE_PENALTY_VAGUE);
            }
            case RECALLED -> {
                newLevel = currentLevel + 1;
                newEase = currentEase;
            }
            // INSTANT
            default -> {
                newLevel = currentLevel + 2;
                newEase = currentEase.add(WordConstant.EASE_BONUS_INSTANT);
            }
        }

        newLevel = clampLevel(newLevel);
        newEase = clampEase(newEase);
        int newInterval = GRADIENT[newLevel];

        // 答错当天重来，答对按新间隔顺延
        LocalDateTime dueTime = qualityEnum == ReviewQualityEnum.FORGOT ? now : now.plusDays(newInterval);

        // 梯度爬到 60 天本身就意味着一路答对（任何一次答错都会退级），再要求本次也答对
        boolean canGraduate = qualityEnum.isPassed() && newInterval >= WordConstant.GRADUATE_INTERVAL_DAYS;

        return Sm2Result.builder()
                .easeFactor(newEase)
                .intervalDays(newInterval)
                .dueTime(dueTime)
                .reviewCount((reviewCount == null ? 0 : reviewCount) + 1)
                .canGraduate(canGraduate)
                .build();
    }

    /**
     * 间隔天数 → 梯度级别
     * <p>
     * 取「不大于 intervalDays 的最大梯度值」所在下标；小于 1 天视为尚未进入梯度。
     */
    private static int levelOf(Integer intervalDays) {
        if (intervalDays == null || intervalDays < GRADIENT[0]) {
            return LEVEL_NOT_STARTED;
        }
        int level = 0;
        for (int i = 0; i < GRADIENT.length; i++) {
            if (GRADIENT[i] <= intervalDays) {
                level = i;
            } else {
                break;
            }
        }
        return level;
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /**
     * 钳制难度系数到 [1.30, 2.80]，并统一到 decimal(4,2) 的精度
     */
    private static BigDecimal clampEase(BigDecimal ease) {
        BigDecimal scaled = ease.setScale(WordConstant.EASE_FACTOR_SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(WordConstant.MIN_EASE_FACTOR) < 0) {
            return WordConstant.MIN_EASE_FACTOR;
        }
        if (scaled.compareTo(WordConstant.MAX_EASE_FACTOR) > 0) {
            return WordConstant.MAX_EASE_FACTOR;
        }
        return scaled;
    }

    /**
     * 复习计算结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sm2Result {

        /**
         * 新的难度系数，已钳制在 [1.30, 2.80]
         */
        private BigDecimal easeFactor;

        /**
         * 新的复习间隔（天）
         */
        private Integer intervalDays;

        /**
         * 下次复习时间。quality=0 时即为当前时间（今天重来）
         */
        private LocalDateTime dueTime;

        /**
         * 累计复习次数（已 +1）
         */
        private Integer reviewCount;

        /**
         * 是否可毕业：间隔已达 60 天且本次答对，上层据此把 mastery 置为已掌握并移出复习池
         */
        private boolean canGraduate;
    }
}
