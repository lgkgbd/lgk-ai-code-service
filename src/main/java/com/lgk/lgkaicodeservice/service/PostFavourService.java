package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.lgk.lgkaicodeservice.model.entity.PostFavour;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.Wrapper;

/**
 * 帖子收藏表 服务层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
public interface PostFavourService extends IService<PostFavour> {

    /**
     * 帖子收藏
     *
     * @param postId
     * @param loginUser
     * @return
     */
    int doPostFavour(long postId, User loginUser);


    /**
     * 帖子收藏（内部服务）
     *
     * @param userId
     * @param postId
     * @return
     */
    int doPostFavourInner(long userId, long postId);

    Page<Post> listFavourPostByPage(Page<Post> page, QueryWrapper queryWrapper, long favourUserId);
}
