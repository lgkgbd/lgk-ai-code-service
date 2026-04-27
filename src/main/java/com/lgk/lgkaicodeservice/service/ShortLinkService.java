package com.lgk.lgkaicodeservice.service;

/**
 * 短链服务接口
 * <p>
 * 基于 Redis + Redisson RBloomFilter 实现对象存储 key 的短链映射：
 * <ul>
 *   <li>上传文件后将 objectKey 映射为一个短 code（6位 Base62），对外只暴露 code</li>
 *   <li>访问时通过 code 反查 objectKey，再由业务层决定如何构建实际访问 URL</li>
 *   <li>布隆过滤器防止缓存穿透：code 不存在时直接返回 null，不打 Redis</li>
 * </ul>
 * </p>
 */
public interface ShortLinkService {

    /**
     * 为对象存储 key 创建短链，返回短链 code
     * <p>
     * 如果同一个 objectKey 已经存在对应的短链，直接返回已有的 code（幂等）。
     * </p>
     *
     * @param objectKey MinIO 对象键，例如 {@code user_avatar/1/abc-avatar.jpg}
     * @return 短链 code，例如 {@code aB3xY9}
     */
    String createShortLink(String objectKey);

    /**
     * 通过短链 code 解析出对应的对象存储 key
     *
     * @param code 短链 code
     * @return objectKey；code 不存在时返回 {@code null}
     */
    String resolveObjectKey(String code);

    /**
     * 删除短链（对象删除时同步清理）
     *
     * @param code 短链 code
     */
    void deleteShortLink(String code);
}
