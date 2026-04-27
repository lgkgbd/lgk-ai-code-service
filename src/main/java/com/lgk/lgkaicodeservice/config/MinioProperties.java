package com.lgk.lgkaicodeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.minio")
public class MinioProperties {

    /**
     * MinIO 服务端点，例如 http://localhost:9000
     */
    private String endpoint;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 秘密密钥
     */
    private String secretKey;

    /**
     * 默认桶名
     */
    private String bucket;

    /**
     * 文件上传大小限制（字节），默认 1MB
     */
    private long maxFileSizeBytes = 1024 * 1024L;
}
