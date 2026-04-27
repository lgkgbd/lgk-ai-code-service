package com.lgk.lgkaicodeservice.controller;

import cn.hutool.core.io.FileUtil;
import com.lgk.lgkaicodeservice.annotation.AuthCheck;
import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.constant.UserConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.manager.StorageManager;
import com.lgk.lgkaicodeservice.model.dto.file.UploadFileRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.FileUploadBizEnum;
import com.lgk.lgkaicodeservice.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * 文件接口
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private StorageManager storageManager;

    /**
     * 短链访问前缀，与 ShortLinkController 的 {@code @RequestMapping("/s")} 对应。
     * 返回给前端的完整 URL 格式：{@code /api/s/{code}}
     */
    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 文件上传
     * <p>
     * 上传成功后返回完整的短链访问 URL（如 {@code /api/s/aB3xY9}），
     * 前端可直接用作 {@code <img src>}、{@code <a href>} 等属性值，无需额外拼接前缀。
     * </p>
     *
     * @param multipartFile     上传的文件（不超过 1MB）
     * @param uploadFileRequest 上传业务类型
     * @param request           HTTP 请求
     * @return 完整的短链 URL，例如 {@code /api/s/aB3xY9}
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        User loginUser = userService.getLoginUser(request);
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
        try {
            // 上传到 MinIO 并自动创建短链，返回短链 code
            String shortCode = storageManager.uploadMultipartFile(filepath, multipartFile);
            // 拼接完整的短链访问 URL，前端可直接用作 img src / a href
            String shortUrl = String.format("%s/s/%s", contextPath, shortCode);
            log.info("文件上传成功，短链 code={}, url={}, filepath={}", shortCode, shortUrl, filepath);
            return ResultUtils.success(shortUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile     上传文件
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小（统一由 StorageManager 做 1MB 限制，此处做业务层校验）
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp", "gif").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }
}
