package com.lgk.lgkaicodeservice.manager;

import com.lgk.lgkaicodeservice.config.MinioProperties;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.service.ShortLinkService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;

/**
 * 对象存储门面（Facade）
 * <p>
 * 统一对外暴露存储操作，屏蔽具体存储实现细节。
 * Controller / Service 只依赖此类，不直接使用 {@link FileStorageStrategy}。
 * </p>
 * <p>
 * 集成短链系统：文件上传成功后自动通过 {@link ShortLinkService} 创建短链 code，
 * 对外返回 <b>短链 code</b> 而非裸 objectKey，彻底屏蔽存储位置信息。
 * </p>
 *
 * <pre>
 * 上传流程：
 *   1. 校验文件大小（≤ 1MB）
 *   2. 调用 FileStorageStrategy 将文件写入 MinIO，得到 objectKey
 *   3. 调用 ShortLinkService.createShortLink(objectKey)，得到短链 code
 *   4. 返回短链 code 给调用方
 *
 * 解析流程（由 ShortLinkController 处理）：
 *   code → ShortLinkService.resolveObjectKey(code) → objectKey
 *   → 由业务层拼接 MinIO 预签名 URL 或 CDN 地址
 * </pre>
 */
@Slf4j
@Component
public class StorageManager {

    @Resource
    private FileStorageStrategy fileStorageStrategy;

    @Resource
    private MinioProperties minioProperties;

    @Resource
    private ShortLinkService shortLinkService;

    /**
     * 上传本地文件，返回短链 code
     *
     * @param key  对象键（MinIO 内部存储路径），例如 {@code user_avatar/1/uuid-filename.jpg}
     * @param file 待上传本地文件
     * @return 短链 code，例如 {@code aB3xY9}
     */
    public String uploadFile(String key, File file) {
        validateFileSize(file.length());
        String objectKey = fileStorageStrategy.uploadFile(key, file);
        return shortLinkService.createShortLink(objectKey);
    }

    /**
     * 上传 MultipartFile，返回短链 code
     * <p>
     * 直接接收 Spring MVC 的 MultipartFile，内部完成流处理。
     * </p>
     *
     * @param key           对象键（MinIO 内部存储路径）
     * @param multipartFile Spring MVC 上传文件对象
     * @return 短链 code
     */
    public String uploadMultipartFile(String key, MultipartFile multipartFile) {
        validateFileSize(multipartFile.getSize());
        try (InputStream is = multipartFile.getInputStream()) {
            String contentType = multipartFile.getContentType() != null
                    ? multipartFile.getContentType()
                    : "application/octet-stream";
            String objectKey = fileStorageStrategy.uploadStream(key, is, contentType, multipartFile.getSize());
            return shortLinkService.createShortLink(objectKey);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("StorageManager 上传 MultipartFile 失败，key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
    }

    /**
     * 通过短链 code 解析出对象存储 key
     * <p>
     * 便捷方法，供服务层直接使用，避免重复注入 ShortLinkService。
     * </p>
     *
     * @param code 短链 code
     * @return objectKey；code 不存在时返回 {@code null}
     */
    public String resolveObjectKey(String code) {
        return shortLinkService.resolveObjectKey(code);
    }

    /**
     * 删除对象（使用默认桶），同步删除短链
     *
     * @param key      对象键
     * @param shortCode 对应短链 code（传 null 则不删短链）
     */
    public void deleteFile(String key, String shortCode) {
        fileStorageStrategy.deleteFile(key);
        if (shortCode != null) {
            shortLinkService.deleteShortLink(shortCode);
        }
    }

    /**
     * 删除对象（使用默认桶）
     * <p>仅删 MinIO 对象，不清理短链（适合无短链场景）</p>
     *
     * @param key 对象键
     */
    public void deleteFile(String key) {
        fileStorageStrategy.deleteFile(key);
    }

    /**
     * 删除对象（指定桶）
     *
     * @param bucketName 桶名
     * @param key        对象键
     */
    public void deleteFile(String bucketName, String key) {
        fileStorageStrategy.deleteFile(bucketName, key);
    }

    /**
     * 获取默认桶名
     */
    public String getDefaultBucket() {
        return fileStorageStrategy.getDefaultBucket();
    }

    /**
     * 获取文件大小限制（字节）
     */
    public long getMaxFileSizeBytes() {
        return minioProperties.getMaxFileSizeBytes();
    }

    // ----------------------------- 私有方法 -----------------------------

    /**
     * 校验文件大小是否超出限制
     */
    private void validateFileSize(long fileSize) {
        long maxSize = minioProperties.getMaxFileSizeBytes();
        if (fileSize > maxSize) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    String.format("文件大小不能超过 %dKB（当前 %dKB）",
                            maxSize / 1024, fileSize / 1024));
        }
    }
}
