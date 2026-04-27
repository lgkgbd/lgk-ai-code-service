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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 短链服务实现
 * <p>
 * 核心流程：
 * <pre>
 * 创建短链：
 *   1. 从 Redis 自增序列 incr(short:link:seq) 获取唯一 ID
 *   2. 对 ID 做 Base62 编码，得到 6 位短 code
 *   3. 将 code -> objectKey 存入 Redis（短链默认永久有效，可按需设 TTL）
 *   4. 将 code 加入布隆过滤器
 *   5. 返回 code
 *
 * 解析短链：
 *   1. 先查布隆过滤器，code 不存在 → 直接返回 null（防穿透）
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

    @Override
    public String createShortLink(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "objectKey 不能为空");
        }
        // 1. 自增序列 → Base62 编码
        Long seq = stringRedisTemplate.opsForValue().increment(RedisConstant.SHORT_LINK_SEQ_KEY);
        if (seq == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "短链序列生成失败");
        }
        String code = toBase62(seq);
        // 2. 存储映射 short:link:{code} -> objectKey（永久有效，不设 TTL）
        String redisKey = RedisConstant.getShortLinkKey(code);
        stringRedisTemplate.opsForValue().set(redisKey, objectKey);
        // 3. 加入布隆过滤器
        bloomFilter.add(code);
        log.info("短链创建成功，code={}, objectKey={}", code, objectKey);
        return code;
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
     * 将长整型数字转换为 Base62 字符串
     * 长度不足 CODE_LENGTH 时左侧补 '0'
     *
     * @param num 正整数（Redis INCR 从 1 开始）
     * @return Base62 编码字符串
     */
    private String toBase62(long num) {
        if (num <= 0) {
            return "000000";
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62.charAt((int) (num % 62)));
            num /= 62;
        }
        // 补齐 CODE_LENGTH 位
        while (sb.length() < CODE_LENGTH) {
            sb.append('0');
        }
        // 反转（低位先入）
        return sb.reverse().toString();
    }
}
