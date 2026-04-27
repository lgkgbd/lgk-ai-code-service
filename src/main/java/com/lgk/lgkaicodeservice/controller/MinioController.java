package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.manager.StorageManager;
import com.lgk.lgkaicodeservice.manager.minio.MinioBucketManager;
import com.lgk.lgkaicodeservice.manager.minio.MinioObjectManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * MinIO 存储管理接口
 * <p>
 * 提供桶管理（列举、创建、删除）和对象管理（列举、上传、删除、获取临时URL）功能。
 * </p>
 */
@RestController
@RequestMapping("/minio")
@Slf4j
@Tag(name = "MinIO 存储管理")
public class MinioController {

    @Resource
    private MinioBucketManager minioBucketManager;

    @Resource
    private MinioObjectManager minioObjectManager;

    @Resource
    private StorageManager storageManager;

    // ========================== 桶管理 ==========================

    /**
     * 列出所有桶
     */
    @GetMapping("/bucket/list")
    @Operation(summary = "列出所有桶")
    public BaseResponse<List<String>> listBuckets() {
        return ResultUtils.success(minioBucketManager.listBuckets());
    }

    /**
     * 创建桶
     *
     * @param bucketName 桶名（3-63位小写字母/数字/连字符）
     */
    @PostMapping("/bucket/create")
    @Operation(summary = "创建桶")
    public BaseResponse<String> createBucket(@RequestParam String bucketName) {
        boolean created = minioBucketManager.createBucket(bucketName);
        return ResultUtils.success(created ? "创建成功" : "桶已存在");
    }

    /**
     * 删除桶（桶必须为空）
     *
     * @param bucketName 桶名
     */
    @DeleteMapping("/bucket/delete")
    @Operation(summary = "删除桶（桶必须为空）")
    public BaseResponse<String> deleteBucket(@RequestParam String bucketName) {
        minioBucketManager.deleteBucket(bucketName);
        return ResultUtils.success("删除成功");
    }

    /**
     * 检查桶是否存在
     *
     * @param bucketName 桶名
     */
    @GetMapping("/bucket/exists")
    @Operation(summary = "检查桶是否存在")
    public BaseResponse<Boolean> bucketExists(@RequestParam String bucketName) {
        return ResultUtils.success(minioBucketManager.bucketExists(bucketName));
    }

    // ========================== 对象管理 ==========================

    /**
     * 列举桶内对象
     *
     * @param bucketName 桶名（可选，默认使用配置的默认桶）
     * @param prefix     对象键前缀过滤（可选）
     * @param maxKeys    最多返回数量（默认 100）
     */
    @GetMapping("/object/list")
    @Operation(summary = "列举桶内对象")
    public BaseResponse<List<MinioObjectManager.ObjectInfo>> listObjects(
            @RequestParam(required = false) String bucketName,
            @RequestParam(required = false) String prefix,
            @RequestParam(defaultValue = "100") int maxKeys) {
        return ResultUtils.success(minioObjectManager.listObjects(bucketName, prefix, maxKeys));
    }

    /**
     * 上传对象
     * <p>
     * 返回对象的 key，业务层通过短链系统将 key 映射为访问 URL。
     * 文件大小不超过 1MB。
     * </p>
     *
     * @param file   上传文件
     * @param prefix 存储前缀目录（可选，默认 uploads）
     */
    @PostMapping("/object/upload")
    @Operation(summary = "上传对象，返回对象 key")
    public BaseResponse<String> uploadObject(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "uploads") String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + file.getOriginalFilename();
        String key = prefix + "/" + filename;
        String objectKey = storageManager.uploadMultipartFile(key, file);
        return ResultUtils.success(objectKey);
    }

    /**
     * 删除对象
     *
     * @param bucketName 桶名（可选，默认使用配置的默认桶）
     * @param key        对象键
     */
    @DeleteMapping("/object/delete")
    @Operation(summary = "删除对象")
    public BaseResponse<String> deleteObject(
            @RequestParam(required = false) String bucketName,
            @RequestParam String key) {
        minioObjectManager.deleteObject(bucketName, key);
        return ResultUtils.success("删除成功");
    }

    /**
     * 获取对象临时预签名访问 URL
     * <p>
     * 注意：此接口仅供管理侧临时访问使用，正式业务请使用短链服务。
     * </p>
     *
     * @param bucketName    桶名（可选，默认使用配置的默认桶）
     * @param key           对象键
     * @param expireSeconds 有效秒数（默认 604800 = 7天）
     */
    @GetMapping("/object/url")
    @Operation(summary = "获取对象临时预签名 URL（管理侧使用）")
    public BaseResponse<String> getPresignedUrl(
            @RequestParam(required = false) String bucketName,
            @RequestParam String key,
            @RequestParam(defaultValue = "604800") int expireSeconds) {
        return ResultUtils.success(minioObjectManager.getPresignedUrl(bucketName, key, expireSeconds));
    }
}
