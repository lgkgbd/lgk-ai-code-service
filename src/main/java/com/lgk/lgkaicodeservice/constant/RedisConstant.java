package com.lgk.lgkaicodeservice.constant;

public interface RedisConstant {

    /**
     * 用户签到记录的 Redis Key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * 获取用户签到记录的 Redis Key
     * @param year 年份
     * @param userId 用户 id
     * @return 拼接好的 Redis Key
     */
    static String getUserSignInRedisKey(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }

    // ==================== 短链系统相关 ====================

    /**
     * 短链 code → objectKey 映射，Hash 结构
     * field: shortCode, value: objectKey
     * 实际使用 String 结构存储单条：short:link:{code} -> objectKey
     */
    String SHORT_LINK_KEY_PREFIX = "short:link:";

    /**
     * 短链全局自增序列 Key（Base62 编码的唯一 ID 来源）
     */
    String SHORT_LINK_SEQ_KEY = "short:link:seq";

    /**
     * 短链布隆过滤器 Key（Redisson RBloomFilter）
     */
    String SHORT_LINK_BLOOM_KEY = "short:link:bloom";

    /**
     * 获取短链映射 Key
     *
     * @param code 短链码
     * @return Redis Key
     */
    static String getShortLinkKey(String code) {
        return SHORT_LINK_KEY_PREFIX + code;
    }

    // ==================== 单词记忆相关 ====================

    /**
     * 待复习队列 Key 前缀，ZSet 结构
     * 格式: word:due:{userId}，member=userWordId，score=dueTime 时间戳(毫秒)
     * 取当日待复习：ZRANGEBYSCORE 0 now；Redis 失效时用 idx_user_due 回源 DB
     */
    String WORD_DUE_KEY_PREFIX = "word:due:";

    /**
     * 当日新词投放配额 Key 前缀，String 结构
     * 格式: word:newquota:{userId}:{date}，值为当日已投放新词数，TTL 48h
     */
    String WORD_NEW_QUOTA_KEY_PREFIX = "word:newquota:";

    /**
     * 复习打卡热力图 Key 前缀，Bitmap 结构（复用 user_sign_in 的 bitmap 思路）
     * 格式: word:reviewdays:{year}:{userId}，offset=当年第几天
     */
    String WORD_REVIEW_DAYS_KEY_PREFIX = "word:reviewdays:";

    /**
     * 词条缓存 Key 前缀，String 结构
     * 格式: word:dict:{lang}:{spelling}，TTL 7d
     */
    String WORD_DICT_KEY_PREFIX = "word:dict:";

    /**
     * 当日新词投放配额过期时间（秒），48 小时
     */
    long WORD_NEW_QUOTA_TTL_SECONDS = 48 * 60 * 60L;

    /**
     * 词条缓存过期时间（秒），7 天
     */
    long WORD_DICT_TTL_SECONDS = 7 * 24 * 60 * 60L;

    /**
     * 获取用户待复习队列 Key
     *
     * @param userId 用户 id
     * @return Redis Key
     */
    static String getWordDueKey(long userId) {
        return WORD_DUE_KEY_PREFIX + userId;
    }

    /**
     * 获取用户当日新词投放配额 Key
     *
     * @param userId 用户 id
     * @param date   日期，格式 yyyy-MM-dd
     * @return Redis Key
     */
    static String getWordNewQuotaKey(long userId, String date) {
        return String.format("%s%s:%s", WORD_NEW_QUOTA_KEY_PREFIX, userId, date);
    }

    /**
     * 获取用户复习打卡热力图 Key
     *
     * @param year   年份
     * @param userId 用户 id
     * @return Redis Key
     */
    static String getWordReviewDaysKey(int year, long userId) {
        return String.format("%s%s:%s", WORD_REVIEW_DAYS_KEY_PREFIX, year, userId);
    }

    /**
     * 获取词条缓存 Key
     *
     * @param lang     语言，如 en
     * @param spelling 单词原形（小写）
     * @return Redis Key
     */
    static String getWordDictKey(String lang, String spelling) {
        return String.format("%s%s:%s", WORD_DICT_KEY_PREFIX, lang, spelling);
    }

    // ==================== 拍照录入（OCR）====================

    /**
     * 拍照识别任务 Key 前缀，String 结构存 JSON
     * 格式: word:ocr:task:{taskId}，TTL 1h
     * 识别结果确认入库后即失去价值，故只放 Redis 不落库
     */
    String WORD_OCR_TASK_KEY_PREFIX = "word:ocr:task:";

    /**
     * 获取拍照识别任务 Key
     *
     * @param taskId 任务 id
     * @return Redis Key
     */
    static String getWordOcrTaskKey(String taskId) {
        return WORD_OCR_TASK_KEY_PREFIX + taskId;
    }

}
