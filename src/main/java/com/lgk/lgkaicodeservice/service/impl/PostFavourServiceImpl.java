package com.lgk.lgkaicodeservice.service.impl;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.service.PostService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgk.lgkaicodeservice.model.entity.PostFavour;
import com.lgk.lgkaicodeservice.mapper.PostFavourMapper;
import com.lgk.lgkaicodeservice.service.PostFavourService;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 帖子收藏表 服务层实现。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service
public class PostFavourServiceImpl extends ServiceImpl<PostFavourMapper, PostFavour>  implements PostFavourService{

    @Resource
    private PostService postService;

    /**
     * 帖子收藏
     *
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostFavour(long postId, User loginUser) {
        // 判断是否存在
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已帖子收藏
        long userId = loginUser.getId();
        // 每个用户串行帖子收藏
        // 锁必须要包裹住事务方法
        PostFavourService postFavourService = (PostFavourService) AopContext.currentProxy();
        synchronized (String.valueOf(userId).intern()) {
            return postFavourService.doPostFavourInner(userId, postId);
        }
    }

    /**
     * 封装了事务的方法
     *
     * @param userId
     * @param postId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int doPostFavourInner(long userId, long postId) {

        QueryWrapper postFavourQueryWrapper = new QueryWrapper();
        postFavourQueryWrapper.eq("postId", postId);
        postFavourQueryWrapper.eq("userId", userId);
        PostFavour oldPostFavour = this.getOne(postFavourQueryWrapper);
        boolean result;
        // 已收藏
        if (oldPostFavour != null) {
            result = this.remove(postFavourQueryWrapper);
            if (result) {
                // 帖子收藏数 - 1
                result = UpdateChain.of(Post.class)
                        .setRaw("favourNum", "favourNum - 1")
                        .where("id = ?", postId)  // 直接使用字符串条件
                        .update();

                return result ? -1 : 0;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        } else {
            // 未帖子收藏
            PostFavour postFavour = new PostFavour();
            postFavour.setUserId(userId);
            postFavour.setPostId(postId);
            result = this.save(postFavour);
            if (result) {
                // 帖子收藏数 + 1
                result = UpdateChain.of(Post.class)
                        .setRaw("favourNum", "favourNum + 1")
                        .where("id = ?", postId)  // 直接使用字符串条件
                        .update();
                return result ? 1 : 0;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
    }

    @Override
    public Page<Post> listFavourPostByPage(Page<Post> page, QueryWrapper queryWrapper, long favourUserId) {
        if (favourUserId <= 0) {
            return new Page<>();
        }

        // 先查询用户收藏的帖子ID
        List<PostFavour> favourList = this.list(
                QueryWrapper.create().eq(PostFavour::getUserId, favourUserId)
        );

        if (favourList.isEmpty()) {
            return new Page<>();
        }

        // 提取帖子ID列表
        List<Long> postIds = favourList.stream()
                .map(PostFavour::getPostId)
                .collect(Collectors.toList());

        // 构建帖子查询条件
        QueryWrapper postQueryWrapper = queryWrapper
                .in(Post::getId, postIds);

        // 使用 PostService 查询帖子
        return postService.page(page, postQueryWrapper);
    }
}
