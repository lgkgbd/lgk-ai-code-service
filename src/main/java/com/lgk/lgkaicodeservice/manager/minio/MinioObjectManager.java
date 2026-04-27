package com.lgk.lgkaicodeservice.manager.minio;

import com.lgk.lgkaicodeservice.config.MinioProperties;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 对象管理器
 * <p>
 * 封装对象的列举、删除、获取预签名 URL 等操作，供管理接口调用。
 * 注意：上传操作统一走 {@link com.lgk.lgkaicodeservice.manager.StorageManager}，
 * 此处只做管理侧操作。
 * </p>
 */
@Slf4j
@Component
public class MinioObjectManager {

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioProperties minioProperties;

    /**
     * 列举桶内对象（分页，最多返回 maxKeys 条）
     *
     * @param bucketName 桶名（为空则用默认桶）
     * @param prefix     对象键前缀过滤，可为空
     * @param maxKeys    最多返回数量，默认 100
     * @return 对象信息列表
     */
    public List<ObjectInfo> listObjects(String bucketName, String prefix, int maxKeys) {
        String bucket = resolveBucket(bucketName);
        List<ObjectInfo> result = new ArrayList<>();
        try {
            ListObjectsArgs.Builder builder = ListObjectsArgs.builder()
                    .bucket(bucket)
                    .maxKeys(maxKeys <= 0 ? 100 : maxKeys)
                    .recursive(true);
            if (prefix != null && !prefix.isBlank()) {
                builder.prefix(prefix);
            }
            Iterable<Result<Item>> objects = minioClient.listObjects(builder.build());
            for (Result<Item> obj : objects) {
                Item item = obj.get();
                ObjectInfo info = new ObjectInfo();
                info.setKey(item.objectName());
                info.setSize(item.size());
                info.setLastModified(item.lastModified() != null
                        ? item.lastModified().toString() : null);
                result.add(info);
            }
            return result;
        } catch (Exception e) {
            log.error("MinIO 列举对象失败，bucket={}, prefix={}", bucket, prefix, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "列举对象失败：" + e.getMessage());
        }
    }

    /**
     * 删除指定对象
     *
     * @param bucketName 桶名（为空则用默认桶）
     * @param key        对象键
     */
    public void deleteObject(String bucketName, String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对象键不能为空");
        }
        String bucket = resolveBucket(bucketName);
        String objectKey = normalizeKey(key);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
            log.info("MinIO 删除对象成功，bucket={}, key={}", bucket, objectKey);
        } catch (Exception e) {
            log.error("MinIO 删除对象失败，bucket={}, key={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除对象失败：" + e.getMessage());
        }
    }

    /**
     * 获取对象的临时预签名访问 URL（有效期 7 天）
     * <p>
     * 注意：正式业务访问链接请使用短链服务，此接口仅供管理侧临时访问使用。
     * </p>
     *
     * @param bucketName 桶名（为空则用默认桶）
     * @param key        对象键
     * @param expireSeconds 有效秒数（默认 604800 = 7天）
     * @return 预签名 URL
     */
    public String getPresignedUrl(String bucketName, String key, int expireSeconds) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对象键不能为空");
        }
        String bucket = resolveBucket(bucketName);
        String objectKey = normalizeKey(key);
        int expire = expireSeconds <= 0 ? 604800 : expireSeconds;
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expire)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO 获取预签名URL失败，bucket={}, key={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取访问链接失败：" + e.getMessage());
        }
    }

    // ----------------------------- 私有方法 -----------------------------

    private String resolveBucket(String bucketName) {
        return (bucketName == null || bucketName.isBlank())
                ? minioProperties.getBucket()
                : bucketName;
    }

    private String normalizeKey(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }

    /**
     * 对象信息 DTO
     */
    @Data
    public static class ObjectInfo {
        private String key;
        private long size;
        private String lastModified;
    }
}
