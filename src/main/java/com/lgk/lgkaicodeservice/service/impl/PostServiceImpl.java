package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.lgk.lgkaicodeservice.constant.CommonConstant;
import com.lgk.lgkaicodeservice.model.dto.post.PostEsDTO;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.UserVO;
import com.lgk.lgkaicodeservice.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



/**
 * 帖子表 服务层实现。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Resource
    private ElasticsearchTemplate elasticsearchTemplate;

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
        long pageNum = postQueryRequest.getPageNum();
        long pageSize = postQueryRequest.getPageSize();
        
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

    @Override
    public Page<Post> searchFromEs(PostQueryRequest postQueryRequest) {
        Long id = postQueryRequest.getId();
        Long notId = postQueryRequest.getNotId();
        String searchText = postQueryRequest.getSearchText();
        String title = postQueryRequest.getTitle();
        String content = postQueryRequest.getContent();
        List<String> tagList = postQueryRequest.getTags();
        List<String> orTagList = postQueryRequest.getOrTags();
        Long userId = postQueryRequest.getUserId();
        Integer priority = postQueryRequest.getPriority();
        // es 起始页为 0
        long pageNum = postQueryRequest.getPageNum() - 1;
        long pageSize = postQueryRequest.getPageSize();
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();

        // 1. 使用新的 Builder 构建布尔查询
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        // 2. 构建 filter 条件
        // filter 条件被收集到一个 List 中，然后一次性添加到 bool 查询中
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.term(t -> t.field("isDelete").value(0))));

        if (id != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("id").value(id))));
        }
        if (userId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
        }

        // 是否查询精选筛选
//        if (priority != null && priority > 0) {
//            filters.add(Query.of(q -> q.term(t -> t
//                    .field("priority")
//                    .value(priority)
//            )));
//        }

        // 必须包含所有标签 (AND logic)
        if (!CollectionUtils.isEmpty(tagList)) {
            for (String tag : tagList) {
                filters.add(Query.of(q -> q.term(t -> t.field("tags").value(tag))));
            }
        }

        // 包含任何一个标签即可 (OR logic)
        if (!CollectionUtils.isEmpty(orTagList)) {
            BoolQuery.Builder orTagBoolQuery = new BoolQuery.Builder();
            for (String tag : orTagList) {
                orTagBoolQuery.should(Query.of(q -> q.term(t -> t.field("tags").value(tag))));
            }
            orTagBoolQuery.minimumShouldMatch("1");
            filters.add(orTagBoolQuery.build()._toQuery());
        }
        boolQueryBuilder.filter(filters);


        // 3. 构建 mustNot 条件
        if (notId != null) {
            boolQueryBuilder.mustNot(Query.of(q -> q.term(t -> t.field("id").value(notId))));
        }

        // 4. 构建 should 条件 (关键词、标题、内容检索)
        // 所有的 should 条件被收集到一起，实现 OR 的效果
        List<Query> shoulds = new ArrayList<>();
        if (StringUtils.hasText(searchText)) {
            shoulds.add(Query.of(q -> q.match(m -> m.field("title").query(searchText))));
            shoulds.add(Query.of(q -> q.match(m -> m.field("content").query(searchText))));
        }
        if (StringUtils.hasText(title)) {
            shoulds.add(Query.of(q -> q.match(m -> m.field("title").query(title))));
        }
        if (StringUtils.hasText(content)) {
            shoulds.add(Query.of(q -> q.match(m -> m.field("content").query(content))));
        }

        if (!shoulds.isEmpty()) {
            boolQueryBuilder.should(shoulds);
            boolQueryBuilder.minimumShouldMatch("1");
        }

        // 5. 构建排序
        List<SortOptions> sorts = new ArrayList<>();
        if (StringUtils.hasText(sortField)) {
            // 使用 lambda 表达式构建排序选项
            sorts.add(SortOptions.of(s -> s.field(f -> f
                    .field(sortField)
                    .order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.Asc : SortOrder.Desc)
            )));
        }
        // 如果没有指定排序字段，Elasticsearch 默认会按分数 (_score) 排序，所以不需要像旧代码一样显式添加 scoreSort()

        // 6. 分页 (保持不变)
        PageRequest pageRequest = PageRequest.of((int) pageNum, (int) pageSize);

        // 7. 构造查询 (使用 NativeQueryBuilder)
        NativeQuery searchQuery = new NativeQueryBuilder()
                .withQuery(boolQueryBuilder.build()._toQuery())
                .withPageable(pageRequest)
                .withSort(sorts) // withSort 现在接收 List<SortOptions>
                .build();

        // 8. 执行查询 (方法签名保持不变)
        SearchHits<PostEsDTO> searchHits = elasticsearchTemplate.search(searchQuery, PostEsDTO.class);

        Page<Post> page = new Page<>();
        page.setTotalRow(searchHits.getTotalHits()); //TODO 需要验证 这个是总数吗？
        List<Post> resourceList = new ArrayList<>();

        // TODO 查出结果后，从 db 获取最新动态数据（比如点赞数、收藏数、浏览数）
        if (searchHits.hasSearchHits()){
            List<SearchHit<PostEsDTO>> searchHitList = searchHits.getSearchHits();
            List<Long> postIdList = searchHitList.stream().map(searchHit -> searchHit.getContent().getId())
                    .collect(Collectors.toList());
            // 从数据库中取出更完整的数据
            List<Post> postList = this.getMapper().selectListByIds(postIdList); //TODO 需要验证
            if(postList != null && postList.size() > 0) {
                Map<Long, List<Post>> idPostMap = postList.stream().collect(Collectors.groupingBy(Post::getId));
                postIdList.forEach(postId -> {
                    if (idPostMap.containsKey(postId)) {
                        resourceList.add(idPostMap.get(postId).get(0));
                    } else {
                        //从 es 中删除 db 以物理删除的数据
                        String delete = elasticsearchTemplate.delete(String.valueOf(postId), PostEsDTO.class);//TODO    这里可以改成批量删除
                        log.info("delete post {}", delete);
                    }
                });
            }
        }
        page.setRecords(resourceList);
        return page;
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
