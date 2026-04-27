package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.lgk.lgkaicodeservice.constant.RedisConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.service.ShortLinkService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 短链服务实现
 * <p>
 * 核心流程：
 * <pre>
 * 创建短链（随机码 + SETNX 防碰撞）：
 *   1. 用 SecureRandom 生成 6 位随机 Base62 码
 *   2. 用 Redis SETNX 原子写入 short:link:{code} -> objectKey（碰撞则重试，上限 10 次）
 *   3. 将 code 加入布隆过滤器
 *   4. 返回 code
 *
 * 解析短链：
 *   1. 先查布隆过滤器，code 一定不存在 → 直接返回 null（防穿透）
 *   2. 查 Redis short:link:{code} → 得到 objectKey
 *   3. objectKey 为空说明已过期或被删 → 返回 null
 * </pre>
 * </p>
 */
@Slf4j
@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    /** Base62 字符集 */
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 短链 code 最小长度（Base62(62^6) ≈ 568 亿个，足够用） */
    private static final int CODE_LENGTH = 6;

    /** 布隆过滤器预期插入量（可按需调整） */
    private static final long BLOOM_EXPECTED_INSERTIONS = 10_000_000L;

    /** 布隆过滤器误判率 */
    private static final double BLOOM_FALSE_PROBABILITY = 0.001;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /** Redisson 布隆过滤器 */
    private RBloomFilter<String> bloomFilter;

    /**
     * 服务启动时初始化布隆过滤器
     * tryInit：如果已初始化则跳过，保证幂等
     */
    @PostConstruct
    public void init() {
        bloomFilter = redissonClient.getBloomFilter(RedisConstant.SHORT_LINK_BLOOM_KEY);
        bloomFilter.tryInit(BLOOM_EXPECTED_INSERTIONS, BLOOM_FALSE_PROBABILITY);
        log.info("短链布隆过滤器初始化完成，预期容量={}，误判率={}", BLOOM_EXPECTED_INSERTIONS, BLOOM_FALSE_PROBABILITY);
    }

    /**
     * SecureRandom 实例（线程安全，重用）
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 随机码最大重试次数（防碰撞） */
    private static final int MAX_RETRY = 10;

    @Override
    public String createShortLink(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "objectKey 不能为空");
        }
        // 用 SecureRandom 生成随机 code，SETNX 防碰撞，上限 MAX_RETRY 次
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = generateRandomCode();
            String redisKey = RedisConstant.getShortLinkKey(code);
            // SETNX 原子写入：key 不存在才写入，成功返回 true
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, objectKey);
            if (Boolean.TRUE.equals(ok)) {
                bloomFilter.add(code);
                log.info("短链创建成功，code={}, objectKey={}", code, objectKey);
                return code;
            }
            log.debug("短链随机码碰撞，code={}，重试第 {} 次", code, i + 1);
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "短链生成失败（碰撞次数超限），请稍后重试");
    }

    @Override
    public String resolveObjectKey(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        // 1. 布隆过滤器预判：code 一定不存在时直接返回（防穿透）
        if (!bloomFilter.contains(code)) {
            log.debug("短链 code={} 不在布隆过滤器中，直接返回 null", code);
            return null;
        }
        // 2. 查 Redis
        String objectKey = stringRedisTemplate.opsForValue().get(RedisConstant.getShortLinkKey(code));
        if (StrUtil.isBlank(objectKey)) {
            log.warn("短链 code={} 在布隆过滤器中存在，但 Redis 无数据（已过期或被删除）", code);
            return null;
        }
        return objectKey;
    }

    @Override
    public void deleteShortLink(String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        stringRedisTemplate.delete(RedisConstant.getShortLinkKey(code));
        // 注意：布隆过滤器不支持删除元素（这是 Bloom Filter 特性），
        // 删除后若再次查询同一 code，会命中布隆过滤器但 Redis 返回 null（误判处理路径），属于正常行为。
        log.info("短链删除成功，code={}", code);
    }

    // ----------------------------- 私有方法 -----------------------------

    /**
     * 用 SecureRandom 生成随机 Base62 字符串
     *
     * @return 随机短码，例如 {@code aB3xY9}
     */
    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(secureRandom.nextInt(BASE62.length())));
        }
        return sb.toString();
    }
}
