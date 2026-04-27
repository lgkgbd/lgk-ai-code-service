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
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
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
     * 通过短链 code 直接转发对应文件流（管道式零拷贝流传输）
     * <p>
     * 核心设计：
     * <ul>
     *   <li>禁用 Servlet response buffer → 数据直接从 MinIO InputStream 管道到客户端 OutputStream</li>
     *   <li>支持 HTTP Range 请求 → 视频/大文件可 seek</li>
     *   <li>设置 Content-Length / Cache-Control / Accept-Ranges 响应头</li>
     * </ul>
     *
     * @param code 短链 code
     * @return 文件流；code 不存在时返回 404
     */
    @GetMapping("/{code}")
    public ResponseEntity<StreamingResponseBody> resolve(
            @PathVariable("code") String code,
            jakarta.servlet.http.HttpServletResponse servletResponse,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
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
        long fileSize = resolveFileSize(fileStream);

        log.debug("短链访问成功，code={}, objectKey={}, contentType={}, size={}", code, objectKey, contentType, fileSize);

        // 解析 Range 请求头（支持断点续传 / 视频 seek）
        RangeRequest range = parseRange(rangeHeader, fileSize);

        // 构建响应头
        HttpHeaders headers = buildResponseHeaders(contentType, fileSize, range, objectKey);

        // ★ 关键：禁用 Servlet response buffer，实现真正的管道式流式传输
        // 不设 buffer 或设为 0 → 每次 write 立即 flush 到 socket，不积压在内存中
        servletResponse.setBufferSize(0);

        StreamingResponseBody body = outputStream -> {
            try (InputStream is = fileStream) {
                // 如果是 Range 请求，跳转到指定位置
                if (range != null && range.start > 0) {
                    is.skip(range.start);
                }
                byte[] buf = new byte[8192];  // 8KB 缓冲区，仅用于 I/O 分块，不会累积在内存
                int len;
                long remaining = (range != null) ? (range.end - range.start + 1) : -1;
                while (remaining != 0 && (len = is.read(buf, 0,
                        remaining > 0 ? (int) Math.min(buf.length, remaining) : buf.length)) != -1) {
                    outputStream.write(buf, 0, len);
                    outputStream.flush();  // 每次写完立即 flush 到 socket
                    if (remaining > 0) {
                        remaining -= len;
                    }
                }
            } catch (IOException e) {
                // ClientAbortException: 客户端断开连接（用户关闭页面/取消下载），属于正常行为
                if (clientDisconnected(e)) {
                    log.debug("客户端已断开连接，停止流转发，code={}", code);
                } else {
                    log.error("短链流转发异常，code={}, objectKey={}", code, objectKey, e);
                    throw e;  // 非客户端断开的异常重新抛出，让 Spring 框架处理
                }
            } catch (Exception e) {
                log.error("短链流转发异常，code={}, objectKey={}", code, objectKey, e);
                throw e;
            }
        };

        return new ResponseEntity<>(body, headers, range != null ? 206 : 200);
    }

    /**
     * 尝试解析文件大小（非必须，失败不影响主流程）
     */
    private long resolveFileSize(InputStream fileStream) {
        try {
            return fileStream.available() > 0 ? fileStream.available() : -1;
        } catch (IOException ignored) {
            return -1;
        }
    }

    /**
     * 判断是否为客户端主动断开连接异常
     * <p>
     * Tomcat: org.apache.catalina.connector.ClientAbortException
     * Jetty: org.eclipse.jetty.io.EofException
     * Undertow: java.io.IOException "Connection reset by peer"
     * </p>
     */
    private boolean clientDisconnected(IOException e) {
        String className = e.getClass().getName();
        String msg = e.getMessage();
        return className.contains("ClientAbortException")
                || className.contains("EofException")
                || (msg != null && (msg.contains("Broken pipe")
                        || msg.contains("Connection reset")
                        || msg.contains("connection was aborted")));
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

    // ==================== Range 请求支持 ====================

    /**
     * HTTP Range 请求解析结果
     */
    private static class RangeRequest {
        final long start;
        final long end;

        RangeRequest(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * 解析 HTTP Range 请求头
     * <p>
     * 支持格式：bytes=start-end, bytes=start-, bytes=-suffixLength
     *
     * @param rangeHeader 原始 Range 请求头值
     * @param fileSize    文件总大小（未知时传 -1）
     * @return Range 请求信息；无法解析或无效时返回 null（返回完整文件）
     */
    private static RangeRequest parseRange(String rangeHeader, long fileSize) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null;
        }
        try {
            String range = rangeHeader.substring(6).trim();
            int dash = range.indexOf('-');
            if (dash < 0) return null;

            String startStr = range.substring(0, dash);
            String endStr = range.substring(dash + 1);

            if (startStr.isEmpty()) {
                // bytes=-N: 最后 N 字节
                long suffixLength = Long.parseLong(endStr);
                if (fileSize > 0 && suffixLength > 0) {
                    long start = Math.max(0, fileSize - suffixLength);
                    return new RangeRequest(start, fileSize - 1);
                }
                return null;
            } else if (endStr.isEmpty()) {
                // bytes=N-: 从 N 到末尾
                long start = Long.parseLong(startStr);
                if (fileSize > 0) {
                    return new RangeRequest(start, fileSize - 1);
                }
                return new RangeRequest(start, -1);  // 文件大小未知，end 标记为 -1
            } else {
                // bytes=N-M
                long start = Long.parseLong(startStr);
                long end = Long.parseLong(endStr);
                if (start > end) return null;
                return new RangeRequest(start, end);
            }
        } catch (NumberFormatException e) {
            return null;  // 格式非法 → 返回完整文件
        }
    }

    /**
     * 构建流式传输响应头
     *
     * @param contentType 内容类型
     * @param fileSize   文件大小（-1 表示未知）
     * @param range      Range 请求信息（null 表示非 Range 请求）
     * @param objectKey  对象键（用于提取文件名设置 Content-Disposition）
     */
    private HttpHeaders buildResponseHeaders(String contentType, long fileSize,
                                             RangeRequest range, String objectKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setCacheControl(CacheControl.noCache());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        // Content-Length / Content-Range
        if (range != null && fileSize > 0) {
            headers.set("Content-Range", String.format("bytes %d-%d/%d", range.start, range.end, fileSize));
            headers.setContentLength(range.end - range.start + 1);
        } else if (fileSize > 0) {
            headers.setContentLength(fileSize);
        }

        // Content-Disposition: inline（图片/视频直接在浏览器展示）+ 文件名
        String filename = extractFilename(objectKey);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + filename + "\"");

        return headers;
    }

    /**
     * 从 objectKey 中提取文件名
     * <p>
     * 例如 user_avatar/1/abc-avatar.jpg → abc-avatar.jpg
     */
    private static String extractFilename(String objectKey) {
        if (objectKey == null) return "download";
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
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
