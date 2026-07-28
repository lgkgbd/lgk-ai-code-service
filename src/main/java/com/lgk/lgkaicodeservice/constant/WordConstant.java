package com.lgk.lgkaicodeservice.constant;

import java.math.BigDecimal;

/**
 * 单词记忆功能相关常量
 * <p>
 * 设计依据见 docs/单词记忆功能-开发计划.md 与 sql/word_memory.sql
 */
public interface WordConstant {

    // ==================== 录入 ====================

    /**
     * 单次录入的候选词上限，超出部分截断并在返回值里标记
     */
    int MAX_CAPTURE_WORDS = 50;

    /**
     * 遇见记录原句长度上限，对齐 user_word_sighting.sentence varchar(1024)
     */
    int MAX_SENTENCE_LENGTH = 1024;

    /**
     * 私人笔记长度上限，对齐 user_word.note varchar(1024)
     */
    int MAX_NOTE_LENGTH = 1024;

    /**
     * 单词拼写长度上限，对齐 word_dict.spelling varchar(128)
     */
    int MAX_SPELLING_LENGTH = 128;

    /**
     * 默认语言，预留多语言（word_dict.lang）
     */
    String DEFAULT_LANG = "en";

    // ==================== 录入渠道（user_word_sighting.channel）====================

    /**
     * 站内只粘一个词，无上下文
     */
    String CHANNEL_MANUAL = "manual";

    /**
     * 站内粘整句，自动拆词并留句
     */
    String CHANNEL_PASTE = "paste";

    /**
     * 浏览器书签小工具，脚本自动抓选中词 + 原句 + 标题 + URL
     */
    String CHANNEL_BOOKMARKLET = "bookmarklet";

    /**
     * 浏览器插件（书签小工具的后续升级形态）
     */
    String CHANNEL_EXTENSION = "extension";

    // ==================== 词书 ====================

    /**
     * 生词本的每日新词投放量：不限，录入即入池（push）
     */
    int DAILY_NEW_LIMIT_UNLIMITED = -1;

    /**
     * 预置词书默认每日新词投放量（pull）
     */
    int DEFAULT_DAILY_NEW_LIMIT = 20;

    /**
     * 个人生词本名称
     */
    String PERSONAL_BOOK_NAME = "我的生词本";

    // ==================== 简化版 SM-2 ====================

    /**
     * 复习间隔梯度（天）。答对进级、答错退级，越界钳制在首尾
     */
    int[] REVIEW_INTERVAL_GRADIENT = {1, 2, 4, 7, 15, 30, 60};

    /**
     * 难度系数初始值
     */
    BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    /**
     * 难度系数下限，必须钳制，否则会被连续答错击穿
     */
    BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");

    /**
     * 难度系数上限
     */
    BigDecimal MAX_EASE_FACTOR = new BigDecimal("2.80");

    /**
     * quality=0 完全忘记的难度系数惩罚
     */
    BigDecimal EASE_PENALTY_FORGOT = new BigDecimal("0.20");

    /**
     * quality=1 模糊的难度系数惩罚
     */
    BigDecimal EASE_PENALTY_VAGUE = new BigDecimal("0.10");

    /**
     * quality=3 秒答的难度系数奖励
     */
    BigDecimal EASE_BONUS_INSTANT = new BigDecimal("0.10");

    /**
     * 毕业门槛：间隔达到该天数且本次答对，即可标记为已掌握并移出复习池
     */
    int GRADUATE_INTERVAL_DAYS = 60;

    /**
     * 难度系数小数位，对齐 decimal(4,2)
     */
    int EASE_FACTOR_SCALE = 2;
}
