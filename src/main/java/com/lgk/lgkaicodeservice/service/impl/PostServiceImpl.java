package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.UserVO;
import com.lgk.lgkaicodeservice.service.UserService;
import com.mybatisflex.core.constant.SqlOperator;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.core.update.UpdateWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.dto.post.PostAddRequest;
import com.lgk.lgkaicodeservice.model.dto.post.PostQueryRequest;
import com.lgk.lgkaicodeservice.model.dto.post.PostUpdateRequest;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.mapper.PostMapper;
import com.lgk.lgkaicodeservice.model.vo.PostVO;
import com.lgk.lgkaicodeservice.service.PostService;
import jakarta.annotation.Resource;
import jodd.util.StringUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;



/**
 * 帖子表 服务层实现。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Override
    public Long addPost(PostAddRequest postAddRequest, Long userId) {
        ThrowUtils.throwIf(postAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);

        // 参数校验
        String title = postAddRequest.getTitle();
        String content = postAddRequest.getContent();
        //ThrowUtils.throwIf(!StringUtils.hasText(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(content), ErrorCode.PARAMS_ERROR, "内容不能为空");
        ThrowUtils.throwIf(title.length() > 100, ErrorCode.PARAMS_ERROR, "标题过长");

        // 构建帖子对象
        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setUserId(userId);
        post.setCoverImage(postAddRequest.getCoverImage());
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        post.setThumbNum(0);
        post.setFavourNum(0);
        post.setViewNum(0);
        post.setPriority(0);

        // 处理标签
        List<String> tags = postAddRequest.getTags();
        if (CollUtil.isNotEmpty(tags)) {
            post.setTags(JSONUtil.toJsonStr(tags));
        }

        // 保存到数据库
        boolean result = this.save(post);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建帖子失败");

        return post.getId();
    }

    @Override
    public Boolean updatePost(PostUpdateRequest postUpdateRequest, Long userId) {
        ThrowUtils.throwIf(postUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);

        Long postId = postUpdateRequest.getId();
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR, "帖子id不能为空");

        // 查询原帖子
        Post oldPost = this.getById(postId);
        ThrowUtils.throwIf(oldPost == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(!oldPost.getUserId().equals(userId), ErrorCode.NO_AUTH_ERROR, "无权限修改");

        // 参数校验
        String title = postUpdateRequest.getTitle();
        String content = postUpdateRequest.getContent();
        if (StringUtils.hasText(title)) {
            ThrowUtils.throwIf(title.length() > 512, ErrorCode.PARAMS_ERROR, "标题过长");
            oldPost.setTitle(title);
        }
        if (StringUtils.hasText(content)) {
            oldPost.setContent(content);
        }
        if (StringUtils.hasText(postUpdateRequest.getCoverImage())) {
            oldPost.setCoverImage(postUpdateRequest.getCoverImage());
        }

        // 处理标签
        List<String> tags = postUpdateRequest.getTags();
        if (tags != null) {
            if (CollUtil.isNotEmpty(tags)) {
                oldPost.setTags(JSONUtil.toJsonStr(tags));
            } else {
                oldPost.setTags(null);
            }
        }

        oldPost.setUpdateTime(LocalDateTime.now());

        // 更新到数据库
        boolean result = this.updateById(oldPost);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新帖子失败");

        return true;
    }

    @Override
    public PostVO getPostVO(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);

        Post post = this.getById(id);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        return this.getPostVO(post);
    }

    @Override
    public Page<PostVO> listPostVOByPage(PostQueryRequest postQueryRequest) {
        int pageNum = postQueryRequest.getPageNum();
        int pageSize = postQueryRequest.getPageSize();
        
        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(Post::getIsDelete, 0);

        // 模糊搜索标题和内容
        if (StringUtils.hasText(postQueryRequest.getSearchText())) {
            queryWrapper.and(qw ->{
                qw.like(Post::getTitle, postQueryRequest.getSearchText())
                        .or(Post::getContent).like(postQueryRequest.getSearchText());
            } );
        }

        // 用户id筛选
        if (postQueryRequest.getUserId() != null && postQueryRequest.getUserId() > 0) {
            queryWrapper.and(Post::getUserId).eq(postQueryRequest.getUserId());
        }

        // 是否查询精选筛选
        if (postQueryRequest.getPriority() != null && postQueryRequest.getPriority() > 0) {
            queryWrapper.and(Post::getPriority).gt(0);
        }

        // 排序
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();
        if (StringUtils.hasText(sortField)) {
            boolean isAsc = "ascend".equals(sortOrder);
            switch (sortField) {
                case "createTime":
                    queryWrapper.orderBy(Post::getCreateTime, isAsc);
                    break;
                case "updateTime":
                    queryWrapper.orderBy(Post::getUpdateTime, isAsc);
                    break;
                case "thumbNum":
                    queryWrapper.orderBy(Post::getThumbNum, isAsc);
                    break;
                case "favourNum":
                    queryWrapper.orderBy(Post::getFavourNum, isAsc);
                    break;
                case "viewNum":
                    queryWrapper.orderBy(Post::getViewNum, isAsc);
                    break;
                default:
                    queryWrapper.orderBy(Post::getCreateTime, false);
            }
        } else {
            queryWrapper.orderBy(Post::getCreateTime, false);
        }

        // 分页查询
        Page<Post> postPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);

        // 转换为VO
        Page<PostVO> postVOPage = new Page<>();
        BeanUtils.copyProperties(postPage, postVOPage);
        
        List<PostVO> postVOList = postPage.getRecords().stream()
                .map(this::getPostVO)
                .collect(Collectors.toList());
        postVOPage.setRecords(postVOList);

        return postVOPage;
    }

    @Override
    public Boolean thumbPost(Long postId, Long userId) {
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);

        Post post = this.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        // 增加点赞数
        post.setThumbNum(post.getThumbNum() + 1);
        boolean result = this.updateById(post);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "点赞失败");

        return true;
    }

    @Override
    public Boolean favourPost(Long postId, Long userId) {
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR);

        Post post = this.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        // 增加收藏数
        post.setFavourNum(post.getFavourNum() + 1);
        boolean result = this.updateById(post);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "收藏失败");

        return true;
    }

    @Override
    public void incrementViewCountAsync(Long postId) {
        // 使用原子操作更新数据库
        // MyBatis-Flex 方式：使用 UpdateChain
        UpdateChain.of(Post.class)
                .setRaw("viewNum", "viewNum + 1")
                .where("id = ?", postId)  // 直接使用字符串条件
                .update();
    }



    /**
     * 将Post实体转换为PostVO
     */
    private PostVO getPostVO(Post post) {
        if (post == null) {
            return null;
        }

        PostVO postVO = new PostVO();
        BeanUtils.copyProperties(post, postVO);

        // 处理标签
        String tagsStr = post.getTags();
        if (StringUtils.hasText(tagsStr)) {
            List<String> tags = JSONUtil.toList(tagsStr, String.class);
            postVO.setTags(tags);
        }

        // 1. 关联查询用户信息
        Long userId = post.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        postVO.setUser(userVO);

        return postVO;
    }
}
