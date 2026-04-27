package com.lgk.lgkaicodeservice.controller;

import cn.hutool.core.util.StrUtil;
import com.lgk.lgkaicodeservice.annotation.AuthCheck;
import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.constant.UserConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.service.ShortLinkService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 短链管理接口
 * <p>
 * 负责对象存储 key 与短链 code 之间的映射管理。
 * 前端上传文件后得到 objectKey，调用 /s/create 换取短 code，
 * 后续通过 /s/{code} 解析回 objectKey 再拼接实际访问 URL。
 * </p>
 *
 * <pre>
 * POST /api/s/create          - 为 objectKey 创建短链（需登录）
 * GET  /api/s/{code}          - 解析短链，返回 objectKey
 * DELETE /api/s/{code}        - 删除短链（需登录）
 * </pre>
 */
@RestController
@RequestMapping("/s")
@Slf4j
public class ShortLinkController {

    @Resource
    private ShortLinkService shortLinkService;

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
     * 通过短链 code 解析出对象存储 key
     * <p>
     * 不需要登录，任何人都可以通过 code 拿到 objectKey，
     * 业务层再根据 objectKey 决定是否有权访问实际对象。
     * </p>
     *
     * @param code 短链 code
     * @return objectKey；code 不存在时返回 404
     */
    @GetMapping("/{code}")
    public BaseResponse<String> resolve(@PathVariable("code") String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "code 不能为空");
        }
        String objectKey = shortLinkService.resolveObjectKey(code);
        if (objectKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "短链不存在或已过期");
        }
        return ResultUtils.success(objectKey);
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
