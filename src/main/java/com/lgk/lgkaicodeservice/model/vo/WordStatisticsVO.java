package com.lgk.lgkaicodeservice.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 词库统计（首页工作台）
 */
@Data
public class WordStatisticsVO implements Serializable {

    /** 总词数 */
    private long totalCount;

    /** 今日新增 */
    private long todayNewCount;

    /** 待复习数（dueTime <= now 且未掌握） */
    private long dueCount;

    /** 已掌握数（mastery=3） */
    private long masteredCount;

    /** 连续打卡天数 */
    private long streakDays;

    private static final long serialVersionUID = 1L;
}
