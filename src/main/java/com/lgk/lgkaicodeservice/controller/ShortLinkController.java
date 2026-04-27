package com.lgk.lgkaicodeservice.controller;

import cn.hutool.core.util.StrUtil;
import com.lgk.lgkaicodeservice.annotation.AuthCheck;
import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.constant.UserConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.manager.FileStorageStrategy;
import com.lgk.lgkaicodeservice.service.ShortLinkService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * 短链管理接口
 * <p>
 * 负责对象存储 key 与短链 code 之间的映射管理。
 * 前端上传文件后得到短链 code，后续通过 /s/{code} 直接获取文件流（图片/视频等）。
 * </p>
 *
 * <pre>
 * POST /api/s/create          - 为 objectKey 创建短链（需登录）
 * GET  /api/s/{code}          - 解析短链并转发文件流
 * DELETE /api/s/{code}        - 删除短链（需登录）
 * </pre>
 */
@RestController
@RequestMapping("/s")
@Slf4j
public class ShortLinkController {

    @Resource
    private ShortLinkService shortLinkService;

    @Resource
    private FileStorageStrategy fileStorageStrategy;

    /**
     * 为对象存储 key 创建短链
     *
     * @param objectKey MinIO 对象键，例如 {@code user_avatar/1/abc-avatar.jpg}
     * @return 短链 code，例如 {@code aB3xY9}
     */
    @PostMapping("/create")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<String> createShortLink(@RequestParam("objectKey") String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "objectKey 不能为空");
        }
        String code = shortLinkService.createShortLink(objectKey);
        return ResultUtils.success(code);
    }

    /**
     * 通过短链 code 直接转发对应文件流
     * <p>
     * 根据 objectKey 后缀推断 Content-Type，返回原始文件流（图片/视频等）。
     * 不需要登录，任何人都可以通过 code 访问。
     * </p>
     *
     * @param code 短链 code
     * @return 文件流；code 不存在时返回 404
     */
    @GetMapping("/{code}")
    public ResponseEntity<StreamingResponseBody> resolve(@PathVariable("code") String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "code 不能为空");
        }
        String objectKey = shortLinkService.resolveObjectKey(code);
        if (objectKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "短链不存在或已过期");
        }
        InputStream fileStream = fileStorageStrategy.getFileStream(objectKey);
        if (fileStream == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        }
        // 根据文件后缀推断 Content-Type
        String contentType = detectContentType(objectKey);
        log.debug("短链访问成功，code={}, objectKey={}, contentType={}", code, objectKey, contentType);
        // StreamingResponseBody 配合 ResponseEntity 设置 Content-Type，实现真正的流转发
        StreamingResponseBody body = outputStream -> {
            try (InputStream is = fileStream) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.flush();
            } catch (Exception e) {
                log.error("短链流转发异常，code={}, objectKey={}", code, objectKey, e);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }

    /**
     * 根据 objectKey 后缀推断 Content-Type
     */
    private String detectContentType(String objectKey) {
        if (objectKey == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON_VALUE;
        if (lower.endsWith(".xml")) return MediaType.TEXT_XML_VALUE;
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * 删除短链
     * <p>
     * 通常在删除对象时同步调用，清理 Redis 中的映射关系。
     * </p>
     *
     * @param code 短链 code
     */
    @DeleteMapping("/{code}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteShortLink(@PathVariable("code") String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "code 不能为空");
        }
        shortLinkService.deleteShortLink(code);
        return ResultUtils.success(true);
    }
}
