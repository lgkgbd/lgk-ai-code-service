package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.dto.thumb.ThumbAddRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.lgk.lgkaicodeservice.service.ThumbService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞表 控制层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/thumb")
public class ThumbController {

    @Resource
    private ThumbService thumbService;

    @Resource
    private UserService userService;


    /**
     * 点赞 / 取消点赞
     *
     * @param thumbAddRequest
     * @param request
     * @return resultNum 本次点赞变化数
     */
    @PostMapping("/")
    public BaseResponse<Integer> doThumb(@RequestBody ThumbAddRequest thumbAddRequest,
                                         HttpServletRequest request) {
        if (thumbAddRequest == null || thumbAddRequest.getTargetId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能点赞
        final User loginUser = userService.getLoginUser(request);
        long targetId = thumbAddRequest.getTargetId();
        ThumbTypeEnum type = thumbAddRequest.getType();
        int result = thumbService.doThumb(type, targetId, loginUser);
        return ResultUtils.success(result);
    }

}
