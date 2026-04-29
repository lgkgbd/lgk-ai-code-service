package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.annotation.AuthCheck;
import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.DeleteRequest;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.constant.UserConstant;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.dto.comment.AddCommentRequest;
import com.lgk.lgkaicodeservice.model.dto.comment.CommentQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.CommentVO;
import com.lgk.lgkaicodeservice.service.CommentService;
import com.lgk.lgkaicodeservice.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Resource
    private CommentService commentService;

    @Resource
    private UserService userService;

    /** 发表评论或回复，登录可用 */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Long> addComment(@RequestBody AddCommentRequest request,
                                          HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        Long commentId = commentService.addComment(request, loginUser);
        return ResultUtils.success(commentId);
    }

    /** 删除评论（本人或管理员），登录可用 */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteComment(@RequestBody DeleteRequest deleteRequest,
                                                HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        Boolean result = commentService.deleteComment(deleteRequest.getId(), loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 分页查顶级评论列表，按热度排序，每条携带前3条回复预览。
     * 不限登录，已登录时返回 hasThumb 状态。
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<CommentVO>> listTopCommentByPage(@RequestBody CommentQueryRequest request,
                                                               HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<CommentVO> voPage = commentService.listTopCommentVOByPage(request, httpRequest);
        return ResultUtils.success(voPage);
    }

    /**
     * 分页查某条顶级评论下的全部回复（点"展开更多回复"时调用）。
     * 不限登录，已登录时返回 hasThumb 状态。
     */
    @PostMapping("/reply/list/page")
    public BaseResponse<Page<CommentVO>> listReplyByPage(@RequestBody CommentQueryRequest request,
                                                          HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<CommentVO> voPage = commentService.listReplyVOByPage(request, httpRequest);
        return ResultUtils.success(voPage);
    }
}
