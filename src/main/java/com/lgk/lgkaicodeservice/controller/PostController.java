package com.lgk.lgkaicodeservice.controller;

import cn.hutool.json.JSONUtil;
import com.lgk.lgkaicodeservice.annotation.AuthCheck;
import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.DeleteRequest;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.constant.UserConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.dto.post.PostAddRequest;
import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.dto.post.PostUpdateRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.PostVO;
import com.lgk.lgkaicodeservice.service.PostService;
import com.lgk.lgkaicodeservice.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 帖子表 控制层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    private PostService postService;

    @Resource
    private UserService userService;

    /**
     * 创建帖子
     *
     * @param postAddRequest 帖子创建请求
     * @param request HTTP请求
     * @return 帖子id
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Long> addPost(@RequestBody PostAddRequest postAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postAddRequest == null, ErrorCode.PARAMS_ERROR);
        Post post = new Post();
        BeanUtils.copyProperties(postAddRequest, post);
        List<String> tags = postAddRequest.getTags();
        if (tags != null) {
            post.setTags(JSONUtil.toJsonStr(tags));
        }
        User loginUser = userService.getLoginUser(request);
        Long postId = postService.addPost(postAddRequest, loginUser.getId());
        
        return ResultUtils.success(postId);
    }

    /**
     * 删除帖子
     *
     * @param deleteRequest 删除请求
     * @param request HTTP请求
     * @return 是否成功
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deletePost(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        User loginUser = userService.getLoginUser(request);
        Long postId = deleteRequest.getId();
        
        // 判断是否存在
        PostVO postVO = postService.getPostVO(postId);
        ThrowUtils.throwIf(postVO == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 仅本人或管理员可删除
        if (!postVO.getUserId().equals(loginUser.getId()) && !"admin".equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        
        boolean result = postService.removeById(postId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        
        return ResultUtils.success(true);
    }

    /**
     * 更新帖子
     *
     * @param postUpdateRequest 帖子更新请求
     * @param request HTTP请求
     * @return 是否成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> updatePost(@RequestBody PostUpdateRequest postUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(postUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        
        User loginUser = userService.getLoginUser(request);
        Boolean result = postService.updatePost(postUpdateRequest, loginUser.getId());
        
        return ResultUtils.success(result);
    }

//    /**
//     * 根据id获取帖子（仅管理员可用）
//     *
//     * @param id 帖子id
//     * @return 帖子信息
//     */
//    @GetMapping("/get")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<PostVO> getPostById(Long id) {
//        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
//
//        PostVO postVO = postService.getPostVO(id);
//        ThrowUtils.throwIf(postVO == null, ErrorCode.NOT_FOUND_ERROR);
//
//        return ResultUtils.success(postVO);
//    }

    /**
     * 根据id获取帖子（公开接口）
     *
     * @param id 帖子id
     * @return 帖子信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<PostVO> getPostVOById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        
        PostVO postVO = postService.getPostVO(id);
        ThrowUtils.throwIf(postVO == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 异步去增加浏览量（不阻塞主线程）
        postService.incrementViewCountAsync(id);
        
        return ResultUtils.success(postVO);
    }

    /**
     * 分页获取帖子列表（公开接口）
     *
     * @param postQueryRequest 查询请求
     * @return 分页结果
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PostVO>> listPostVOByPage(@RequestBody PostQueryRequest postQueryRequest) {
        if (postQueryRequest == null) {
            postQueryRequest = new PostQueryRequest();
        }
        
        Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest);
        return ResultUtils.success(postVOPage);
    }

    /**
     * 分页获取当前登录用户创建的帖子列表
     *
     * @param postQueryRequest 查询请求
     * @param request HTTP请求
     * @return 分页结果
     */
    @PostMapping("/my/list/page/vo")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<PostVO>> listMyPostVOByPage(@RequestBody PostQueryRequest postQueryRequest, HttpServletRequest request) {
        if (postQueryRequest == null) {
            postQueryRequest = new PostQueryRequest();
        }
        
        User loginUser = userService.getLoginUser(request);
        postQueryRequest.setUserId(loginUser.getId());
        
        Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest);
        return ResultUtils.success(postVOPage);
    }

//    /**
//     * 点赞帖子
//     *
//     * @param postId 帖子id
//     * @param request HTTP请求
//     * @return 是否成功
//     */
//    @PostMapping("/thumb")
//    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
//    public BaseResponse<Boolean> thumbPost(@RequestParam Long postId, HttpServletRequest request) {
//        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR);
//
//        User loginUser = userService.getLoginUser(request);
//        Boolean result = postService.thumbPost(postId, loginUser.getId());
//
//        return ResultUtils.success(result);
//    }
//
//    /**
//     * 收藏帖子
//     *
//     * @param postId 帖子id
//     * @param request HTTP请求
//     * @return 是否成功
//     */
//    @PostMapping("/favour")
//    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
//    public BaseResponse<Boolean> favourPost(@RequestParam Long postId, HttpServletRequest request) {
//        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR);
//
//        User loginUser = userService.getLoginUser(request);
//        Boolean result = postService.favourPost(postId, loginUser.getId());
//
//        return ResultUtils.success(result);
//    }

    /**
     * 管理员分页获取帖子列表
     *
     * @param postQueryRequest 查询请求
     * @return 分页结果
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<PostVO>> listPostByPage(@RequestBody PostQueryRequest postQueryRequest) {
        if (postQueryRequest == null) {
            postQueryRequest = new PostQueryRequest();
        }
        
        Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest);
        return ResultUtils.success(postVOPage);
    }
}
