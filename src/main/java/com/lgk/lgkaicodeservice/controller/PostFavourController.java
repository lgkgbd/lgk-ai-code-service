package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.dto.postfavour.PostFavourAddRequest;
import com.lgk.lgkaicodeservice.model.dto.thumb.ThumbAddRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.model.vo.PostVO;
import com.lgk.lgkaicodeservice.service.PostService;
import com.lgk.lgkaicodeservice.service.ThumbService;
import com.lgk.lgkaicodeservice.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.lgk.lgkaicodeservice.service.PostFavourService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子收藏表 控制层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/postFavour")
public class PostFavourController {

    @Resource
    private PostFavourService postFavourService;

    @Resource
    private UserService userService;

    @Resource
    private PostService postService;

    /**
     * 收藏 / 取消收藏
     *
     * @param postFavourAddRequest
     * @param request
     * @return resultNum 收藏变化数
     */
    @PostMapping("/")
    public BaseResponse<Integer> doPostFavour(@RequestBody PostFavourAddRequest postFavourAddRequest,
                                              HttpServletRequest request) {
        if (postFavourAddRequest == null || postFavourAddRequest.getPostId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能操作
        final User loginUser = userService.getLoginUser(request);
        long postId = postFavourAddRequest.getPostId();
        int result = postFavourService.doPostFavour(postId, loginUser);
        return ResultUtils.success(result);
    }


    /**
     * 获取我收藏的帖子列表
     *
     * @param postQueryRequest
     * @param request
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<PostVO>> listMyFavourPostByPage(@RequestBody PostQueryRequest postQueryRequest,
                                                             HttpServletRequest request) {
        if (postQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long pageNum = postQueryRequest.getPageNum();
        long size = postQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

//        Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest, request);
//
//        return ResultUtils.success(postVOPage);
        Page<Post> postPage = postFavourService.listFavourPostByPage(new Page<>(pageNum, size),
                postService.getQueryWrapper(postQueryRequest), loginUser.getId());
        return ResultUtils.success(postService.getPostVOPage(postPage,request));
    }

}
