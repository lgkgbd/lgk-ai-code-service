package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.dto.word.WordReviewSubmitRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.WordReviewCardVO;
import com.lgk.lgkaicodeservice.service.UserService;
import com.lgk.lgkaicodeservice.service.WordReviewService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 单词复习 控制层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/word/review")
public class WordReviewController {

    @Resource
    private WordReviewService wordReviewService;

    @Resource
    private UserService userService;

    /**
     * 今日复习队列
     */
    @GetMapping("/today")
    public BaseResponse<List<WordReviewCardVO>> today(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wordReviewService.getTodayQueue(loginUser));
    }

    /**
     * 提交作答，返回本次作答后的新掌握阶段
     */
    @PostMapping("/submit")
    public BaseResponse<Integer> submit(@RequestBody WordReviewSubmitRequest wordReviewSubmitRequest,
                                        HttpServletRequest request) {
        ThrowUtils.throwIf(wordReviewSubmitRequest == null
                || wordReviewSubmitRequest.getUserWordId() == null
                || wordReviewSubmitRequest.getUserWordId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer mastery = wordReviewService.submit(
                wordReviewSubmitRequest.getUserWordId(),
                wordReviewSubmitRequest.getQuality(),
                wordReviewSubmitRequest.getCostMs(),
                loginUser);
        return ResultUtils.success(mastery);
    }

    /**
     * 待复习数（导航角标）
     */
    @GetMapping("/count")
    public BaseResponse<Long> count(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wordReviewService.countDue(loginUser));
    }
}
