package com.lgk.lgkaicodeservice.manager.strategy;

import com.lgk.lgkaicodeservice.config.MinioProperties;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.manager.FileStorageStrategy;
import io.minio.GetObjectArgs;
import io.minio.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * MinIO 文件存储策略实现
 * <p>
 * 实现 {@link FileStorageStrategy}，对接 MinIO / 兼容 S3 协议的对象存储。
 * 若需切换到其他 S3 兼容存储（如 AWS S3、阿里云 OSS），
 * 只需新建对应实现类并注册为 Bean，无需修改调用方。
 * </p>
 */
@Slf4j
@Component
public class MinioStorageStrategy implements FileStorageStrategy {

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioProperties minioProperties;

    @Override
    public String uploadFile(String key, File file) {
        if (file == null || !file.exists()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不存在");
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            String contentType = detectContentType(file.getName());
            return uploadStream(key, fis, contentType, file.length());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 文件上传失败，key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public String uploadStream(String key, InputStream inputStream, String contentType, long size) {
        String bucket = minioProperties.getBucket();
        ensureBucketExists(bucket);
        // key 统一去掉首部斜杠，MinIO 对象键不带前导 /
        String objectKey = normalizeKey(key);
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType);
            if (size > 0) {
                builder.stream(inputStream, size, -1);
            } else {
                builder.stream(inputStream, -1, 10 * 1024 * 1024);
            }
            minioClient.putObject(builder.build());
            log.info("MinIO 上传成功，bucket={}, key={}", bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("MinIO 流上传失败，key={}", objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String key) {
        deleteFile(minioProperties.getBucket(), key);
    }

    @Override
    public void deleteFile(String bucketName, String key) {
        String objectKey = normalizeKey(key);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.info("MinIO 删除成功，bucket={}, key={}", bucketName, objectKey);
        } catch (Exception e) {
            log.error("MinIO 删除失败，bucket={}, key={}", bucketName, objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败：" + e.getMessage());
        }
    }

    @Override
    public String getDefaultBucket() {
        return minioProperties.getBucket();
    }

    // ----------------------------- 私有辅助方法 -----------------------------

    /**
     * 确保桶存在，不存在则自动创建
     */
    private void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("MinIO 自动创建桶：{}", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO 桶检查/创建失败，bucket={}", bucketName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "存储桶初始化失败");
        }
    }

    /**
     * 对象键规范化：去掉前导斜杠
     */
    private String normalizeKey(String key) {
        if (key == null) return "";
        return key.startsWith("/") ? key.substring(1) : key;
    }

    @Override
    public InputStream getFileStream(String key) {
        String objectKey = normalizeKey(key);
        String bucket = minioProperties.getBucket();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            // MinIO SDK 的 ErrorResponseException 继承自 S3ErrorResponseException，
            // 用通用的 Exception 捕获最省事，直接判断异常消息即可
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("NoSuchKey") || msg.contains("object does not exist")) {
                log.warn("MinIO 文件不存在，bucket={}, key={}", bucket, objectKey);
                return null;
            }
            log.error("MinIO 读取文件异常，bucket={}, key={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败：" + e.getMessage());
        }
    }

    /**
     * 根据文件名推断 Content-Type
     */
    private String detectContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }
}
