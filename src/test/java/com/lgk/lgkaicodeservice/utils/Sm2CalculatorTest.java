package com.lgk.lgkaicodeservice.utils;

import com.lgk.lgkaicodeservice.utils.Sm2Calculator.Sm2Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用例清单见 docs/单词记忆功能-开发计划.md 6.1 节
 * <p>
 * 间隔梯度：1 / 2 / 4 / 7 / 15 / 30 / 60
 */
class Sm2CalculatorTest {

    private static final BigDecimal DEFAULT_EASE = new BigDecimal("2.50");

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 27, 10, 30, 0);

    private static final int FORGOT = 0;
    private static final int VAGUE = 1;
    private static final int RECALLED = 2;
    private static final int INSTANT = 3;

    private static Sm2Result calc(String ease, int intervalDays, int reviewCount, int quality) {
        return Sm2Calculator.calculate(new BigDecimal(ease), intervalDays, reviewCount, quality, NOW);
    }

    // ==================== quality=2 想起来了：进一级，easeFactor 不变 ====================

    @Test
    @DisplayName("quality=2 沿梯度逐级推进 0→1→2→4→7→15→30→60")
    void recalledWalksTheGradient() {
        int[] expected = {1, 2, 4, 7, 15, 30, 60};
        int interval = 0;
        for (int i = 0; i < expected.length; i++) {
            Sm2Result result = calc("2.50", interval, i, RECALLED);
            assertEquals(expected[i], result.getIntervalDays(),
                    "第 " + (i + 1) + " 次答对后的间隔");
            assertEquals(new BigDecimal("2.50"), result.getEaseFactor(), "quality=2 不改变 easeFactor");
            assertEquals(NOW.plusDays(expected[i]), result.getDueTime());
            interval = result.getIntervalDays();
        }
    }

    @Test
    @DisplayName("quality=2 到顶后停在 60 天，不越界")
    void recalledCapsAtSixtyDays() {
        Sm2Result result = calc("2.50", 60, 10, RECALLED);
        assertEquals(60, result.getIntervalDays());
        assertEquals(NOW.plusDays(60), result.getDueTime());
    }

    // ==================== quality=3 秒答：进两级，easeFactor +0.10 ====================

    @Test
    @DisplayName("quality=3 一次跳两级：新词→2 天，2→7 天，7→30 天")
    void instantJumpsTwoLevels() {
        assertEquals(2, calc("2.50", 0, 0, INSTANT).getIntervalDays());
        assertEquals(7, calc("2.50", 2, 1, INSTANT).getIntervalDays());
        assertEquals(30, calc("2.50", 7, 2, INSTANT).getIntervalDays());
        assertEquals(60, calc("2.50", 15, 3, INSTANT).getIntervalDays());
    }

    @Test
    @DisplayName("quality=3 easeFactor +0.10")
    void instantRaisesEase() {
        assertEquals(new BigDecimal("2.60"), calc("2.50", 4, 3, INSTANT).getEaseFactor());
    }

    @Test
    @DisplayName("quality=3 到顶后停在 60 天，不越界")
    void instantCapsAtSixtyDays() {
        assertEquals(60, calc("2.50", 30, 8, INSTANT).getIntervalDays());
        assertEquals(60, calc("2.50", 60, 9, INSTANT).getIntervalDays());
    }

    // ==================== quality=1 模糊：退一级，easeFactor -0.10 ====================

    @Test
    @DisplayName("quality=1 退一级：60→30，30→15，4→2，2→1")
    void vagueStepsBack() {
        assertEquals(30, calc("2.50", 60, 9, VAGUE).getIntervalDays());
        assertEquals(15, calc("2.50", 30, 8, VAGUE).getIntervalDays());
        assertEquals(2, calc("2.50", 4, 3, VAGUE).getIntervalDays());
        assertEquals(1, calc("2.50", 2, 2, VAGUE).getIntervalDays());
    }

    @Test
    @DisplayName("quality=1 在梯度起点不会退到 0 天以下")
    void vagueDoesNotUnderflow() {
        assertEquals(1, calc("2.50", 1, 1, VAGUE).getIntervalDays());
        assertEquals(1, calc("2.50", 0, 0, VAGUE).getIntervalDays());
    }

    @Test
    @DisplayName("quality=1 easeFactor -0.10，dueTime 按新间隔顺延")
    void vagueLowersEase() {
        Sm2Result result = calc("2.50", 4, 3, VAGUE);
        assertEquals(new BigDecimal("2.40"), result.getEaseFactor());
        assertEquals(NOW.plusDays(2), result.getDueTime());
    }

    // ==================== quality=0 完全忘记：归 1 天、今天重来、easeFactor -0.20 ====================

    @Test
    @DisplayName("quality=0 无论走到多远都归 1 天")
    void forgotResetsToOneDay() {
        for (int interval : new int[]{0, 1, 2, 4, 7, 15, 30, 60}) {
            assertEquals(1, calc("2.50", interval, 5, FORGOT).getIntervalDays(),
                    "间隔 " + interval + " 天时答错应归 1");
        }
    }

    @Test
    @DisplayName("quality=0 今天重来：dueTime 就是当前时间")
    void forgotIsDueToday() {
        assertEquals(NOW, calc("2.50", 30, 8, FORGOT).getDueTime());
    }

    @Test
    @DisplayName("quality=0 easeFactor -0.20")
    void forgotLowersEase() {
        assertEquals(new BigDecimal("2.30"), calc("2.50", 30, 8, FORGOT).getEaseFactor());
    }

    // ==================== easeFactor 钳制 ====================

    @Test
    @DisplayName("★ easeFactor 下限 1.3 不被击穿：连续答错 20 次仍是 1.30")
    void easeFactorNeverBreaksLowerBound() {
        BigDecimal ease = DEFAULT_EASE;
        for (int i = 0; i < 20; i++) {
            Sm2Result result = Sm2Calculator.calculate(ease, 1, i, FORGOT, NOW);
            ease = result.getEaseFactor();
            assertTrue(ease.compareTo(new BigDecimal("1.30")) >= 0,
                    "第 " + (i + 1) + " 次答错后 easeFactor = " + ease + "，击穿了下限");
        }
        assertEquals(new BigDecimal("1.30"), ease);
    }

    @Test
    @DisplayName("★ 已在下限时继续答错，仍停在 1.30")
    void easeFactorStaysAtLowerBound() {
        assertEquals(new BigDecimal("1.30"), calc("1.30", 1, 5, FORGOT).getEaseFactor());
        assertEquals(new BigDecimal("1.30"), calc("1.35", 1, 5, FORGOT).getEaseFactor());
        assertEquals(new BigDecimal("1.30"), calc("1.30", 1, 5, VAGUE).getEaseFactor());
    }

    @Test
    @DisplayName("easeFactor 上限 2.8 不被突破：连续秒答 20 次仍是 2.80")
    void easeFactorNeverBreaksUpperBound() {
        BigDecimal ease = DEFAULT_EASE;
        for (int i = 0; i < 20; i++) {
            ease = Sm2Calculator.calculate(ease, 60, i, INSTANT, NOW).getEaseFactor();
            assertTrue(ease.compareTo(new BigDecimal("2.80")) <= 0,
                    "第 " + (i + 1) + " 次秒答后 easeFactor = " + ease + "，突破了上限");
        }
        assertEquals(new BigDecimal("2.80"), ease);
    }

    @Test
    @DisplayName("easeFactor 为 null 时按默认 2.50 起算")
    void nullEaseFactorFallsBackToDefault() {
        Sm2Result result = Sm2Calculator.calculate(null, 0, 0, RECALLED, NOW);
        assertEquals(new BigDecimal("2.50"), result.getEaseFactor());
    }

    // ==================== 毕业 ====================

    @Test
    @DisplayName("连续答对到 intervalDays=60 → canGraduate")
    void graduatesAfterConsecutiveSuccess() {
        int interval = 0;
        Sm2Result result = null;
        for (int i = 0; i < 7; i++) {
            result = calc("2.50", interval, i, RECALLED);
            interval = result.getIntervalDays();
            if (interval < 60) {
                assertFalse(result.isCanGraduate(), "间隔 " + interval + " 天时还不能毕业");
            }
        }
        assertEquals(60, interval);
        assertTrue(result.isCanGraduate());
    }

    @Test
    @DisplayName("间隔到 60 天但本次答错 / 模糊 → 不能毕业")
    void doesNotGraduateOnFailure() {
        assertFalse(calc("2.50", 60, 9, FORGOT).isCanGraduate());
        assertFalse(calc("2.50", 60, 9, VAGUE).isCanGraduate());
    }

    @Test
    @DisplayName("答错后从 1 天重新爬，且立刻失去毕业资格")
    void relapseRestartsFromOneDay() {
        Sm2Result lapse = calc("2.50", 60, 9, FORGOT);
        assertEquals(1, lapse.getIntervalDays());
        assertFalse(lapse.isCanGraduate());
        assertEquals(NOW, lapse.getDueTime());

        Sm2Result next = calc("2.30", lapse.getIntervalDays(), 10, RECALLED);
        assertEquals(2, next.getIntervalDays());
    }

    // ==================== 其它 ====================

    @Test
    @DisplayName("reviewCount 每次 +1")
    void reviewCountIncrements() {
        assertEquals(1, calc("2.50", 0, 0, RECALLED).getReviewCount());
        assertEquals(6, calc("2.50", 7, 5, FORGOT).getReviewCount());
        assertEquals(1, Sm2Calculator.calculate(DEFAULT_EASE, 0, null, RECALLED, NOW).getReviewCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 4, 99})
    @DisplayName("非法 quality 抛 IllegalArgumentException")
    void rejectsIllegalQuality(int quality) {
        assertThrows(IllegalArgumentException.class,
                () -> Sm2Calculator.calculate(DEFAULT_EASE, 1, 1, quality, NOW));
    }

    @Test
    @DisplayName("quality 为 null 抛 IllegalArgumentException")
    void rejectsNullQuality() {
        assertThrows(IllegalArgumentException.class,
                () -> Sm2Calculator.calculate(DEFAULT_EASE, 1, 1, null, NOW));
    }

    @Test
    @DisplayName("非梯度值的间隔（如历史脏数据 5 天）向下取到最近一级再计算")
    void handlesOffGradientInterval() {
        // 5 天落在 4 与 7 之间，按 4（第 2 级）算，进一级 → 7
        assertEquals(7, calc("2.50", 5, 3, RECALLED).getIntervalDays());
        // 100 天超出梯度，按 60（顶级）算，退一级 → 30
        assertEquals(30, calc("2.50", 100, 12, VAGUE).getIntervalDays());
    }

    @Test
    @DisplayName("间隔为 null 视为新词")
    void handlesNullInterval() {
        assertEquals(1, Sm2Calculator.calculate(DEFAULT_EASE, null, 0, RECALLED, NOW).getIntervalDays());
    }

    @Test
    @DisplayName("不传 now 的重载走系统当前时间")
    void defaultNowOverload() {
        LocalDateTime before = LocalDateTime.now();
        Sm2Result result = Sm2Calculator.calculate(DEFAULT_EASE, 0, 0, RECALLED);
        assertTrue(!result.getDueTime().isBefore(before.plusDays(1)));
        assertEquals(1, result.getIntervalDays());
    }
}
