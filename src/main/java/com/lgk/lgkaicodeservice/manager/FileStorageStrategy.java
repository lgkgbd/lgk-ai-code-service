package com.lgk.lgkaicodeservice.manager;

import java.io.File;
import java.io.InputStream;

/**
 * 文件存储策略接口
 * <p>
 * 面向接口编程，屏蔽具体存储实现（MinIO、S3、COS 等）。
 * 切换存储提供商只需替换实现类，上层调用方无需改动。
 * </p>
 */
public interface FileStorageStrategy {

    /**
     * 上传文件并返回对象存储中的 key
     *
     * @param key  对象键（存储路径），例如 /user_avatar/1/xxx.jpg
     * @param file 待上传的本地文件
     * @return 对象存储中的 key，供短链系统映射使用
     */
    String uploadFile(String key, File file);

    /**
     * 通过输入流上传文件
     *
     * @param key         对象键
     * @param inputStream 输入流
     * @param contentType 内容类型，例如 image/jpeg
     * @param size        文件大小（字节），-1 表示未知
     * @return 对象存储中的 key
     */
    String uploadStream(String key, InputStream inputStream, String contentType, long size);

    /**
     * 删除对象
     *
     * @param key 对象键
     */
    void deleteFile(String key);

    /**
     * 删除对象（指定桶）
     *
     * @param bucketName 桶名
     * @param key        对象键
     */
    void deleteFile(String bucketName, String key);

    /**
     * 获取默认桶名
     *
     * @return 桶名
     */
    String getDefaultBucket();
}
