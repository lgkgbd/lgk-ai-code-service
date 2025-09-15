package com.lgk.lgkaicodeservice.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.lgk.lgkaicodeservice.model.dto.post.PostAddRequest;
import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.dto.post.PostUpdateRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.vo.PostVO;

/**
 * 帖子表 服务层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
public interface PostService extends IService<Post> {

    /**
     * 创建帖子
     *
     * @param postAddRequest 帖子创建请求
     * @param userId 当前用户id
     * @return 帖子id
     */
    Long addPost(PostAddRequest postAddRequest, Long userId);

    /**
     * 更新帖子
     *
     * @param postUpdateRequest 帖子更新请求
     * @param userId 当前用户id
     * @return 是否成功
     */
    Boolean updatePost(PostUpdateRequest postUpdateRequest, Long userId);

    /**
     * 根据id获取帖子VO
     *
     * @param id 帖子id
     * @return 帖子VO
     */
    PostVO getPostVO(Long id);

    /**
     * 分页查询帖子VO
     *
     * @param postQueryRequest 查询请求
     * @return 分页结果
     */
    Page<PostVO> listPostVOByPage(PostQueryRequest postQueryRequest);

    /**
     * 点赞帖子
     *
     * @param postId 帖子id
     * @param userId 用户id
     * @return 是否成功
     */
    Boolean thumbPost(Long postId, Long userId);

    /**
     * 收藏帖子
     *
     * @param postId 帖子id
     * @param userId 用户id
     * @return 是否成功
     */
    Boolean favourPost(Long postId, Long userId);

    /**
     * 增加帖子浏览数
     * @param postId 帖子id
     */
    void incrementViewCountAsync(Long postId);

    /**
     * 从 ES 查询
     *
     * @param postQueryRequest
     * @return
     */
    Page<Post> searchFromEs(PostQueryRequest postQueryRequest);

}
