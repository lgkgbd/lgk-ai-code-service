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

}
