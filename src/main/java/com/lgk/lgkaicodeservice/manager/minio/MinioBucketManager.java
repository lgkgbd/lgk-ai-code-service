package com.lgk.lgkaicodeservice.manager.minio;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import io.minio.*;
import io.minio.messages.Bucket;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MinIO 桶管理器
 * <p>
 * 封装桶的增删查操作，供管理接口调用。
 * </p>
 */
@Slf4j
@Component
public class MinioBucketManager {

    @Resource
    private MinioClient minioClient;

    /**
     * 列出所有桶名
     */
    public List<String> listBuckets() {
        try {
            List<Bucket> buckets = minioClient.listBuckets();
            return buckets.stream()
                    .map(Bucket::name)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("MinIO 列出桶失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取桶列表失败：" + e.getMessage());
        }
    }

    /**
     * 创建桶（如果不存在则创建）
     *
     * @param bucketName 桶名
     * @return true=新建成功，false=桶已存在
     */
    public boolean createBucket(String bucketName) {
        validateBucketName(bucketName);
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (exists) {
                return false;
            }
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("MinIO 创建桶成功：{}", bucketName);
            return true;
        } catch (Exception e) {
            log.error("MinIO 创建桶失败，bucketName={}", bucketName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建桶失败：" + e.getMessage());
        }
    }

    /**
     * 删除桶（桶必须为空）
     *
     * @param bucketName 桶名
     */
    public void deleteBucket(String bucketName) {
        validateBucketName(bucketName);
        try {
            minioClient.removeBucket(
                    RemoveBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("MinIO 删除桶成功：{}", bucketName);
        } catch (Exception e) {
            log.error("MinIO 删除桶失败，bucketName={}", bucketName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除桶失败（请确认桶是否为空）：" + e.getMessage());
        }
    }

    /**
     * 判断桶是否存在
     */
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
        } catch (Exception e) {
            log.error("MinIO 检查桶存在失败，bucketName={}", bucketName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "检查桶状态失败：" + e.getMessage());
        }
    }

    // ----------------------------- 私有方法 -----------------------------

    private void validateBucketName(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "桶名不能为空");
        }
        // MinIO 桶名规则：3-63 个字符，只允许小写字母、数字和连字符
        if (!bucketName.matches("^[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "桶名格式非法（3-63位小写字母/数字/连字符）");
        }
    }
}
