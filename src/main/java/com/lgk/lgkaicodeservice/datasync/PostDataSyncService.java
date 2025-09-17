package com.lgk.lgkaicodeservice.datasync;

import com.lgk.lgkaicodeservice.model.dto.post.PostEsDTO;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Post数据同步服务
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service
@Slf4j
public class PostDataSyncService {

    @Resource
    private PostService postService;

    @Resource
    private ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 全量同步Post数据到ES
     *
     * @return 同步的数据条数
     */
    public long syncAllPostsToEs() {
        log.info("开始全量同步Post数据到ES");
        
        // 查询所有未删除的Post
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(Post::getIsDelete, 0);
        
        List<Post> posts = postService.list(queryWrapper);
        
        long successCount = 0;
        for (Post post : posts) {
            try {
                syncPostToEs(post);
                successCount++;
            } catch (Exception e) {
                log.error("同步Post到ES失败, ID: {}", post.getId(), e);
            }
        }
        
        log.info("全量同步Post数据完成，总数: {}, 成功: {}", posts.size(), successCount);
        return successCount;
    }

    /**
     * 同步指定Post到ES
     *
     * @param postId Post ID
     * @return 是否成功
     */
    public boolean syncPostToEs(Long postId) {
        Post post = postService.getById(postId);
        if (post == null || post.getIsDelete() == 1) {
            log.warn("Post不存在或已删除, ID: {}", postId);
            return false;
        }
        
        try {
            syncPostToEs(post);
            log.info("Post同步到ES成功, ID: {}", postId);
            return true;
        } catch (Exception e) {
            log.error("Post同步到ES失败, ID: {}", postId, e);
            return false;
        }
    }

    /**
     * 同步Post对象到ES
     *
     * @param post Post对象
     */
    public void syncPostToEs(Post post) {
        PostEsDTO postEsDTO = PostEsDTO.objToDto(post);
        if (postEsDTO != null) {
            // 转换时间格式
            if (post.getCreateTime() != null) {
                postEsDTO.setCreateTime(Date.from(post.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()));
            }
            if (post.getUpdateTime() != null) {
                postEsDTO.setUpdateTime(Date.from(post.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()));
            }
            
            elasticsearchTemplate.save(postEsDTO);
        }
    }

    /**
     * 从ES删除Post
     *
     * @param postId Post ID
     */
    public void deletePostFromEs(Long postId) {
        try {
            elasticsearchTemplate.delete(String.valueOf(postId), PostEsDTO.class);
            log.info("Post从ES删除成功, ID: {}", postId);
        } catch (Exception e) {
            log.error("Post从ES删除失败, ID: {}", postId, e);
            throw e;
        }
    }

    /**
     * 批量删除ES中的Post
     *
     * @param postIds Post ID列表
     */
    public void batchDeletePostsFromEs(List<Long> postIds) {
        for (Long postId : postIds) {
            try {
                deletePostFromEs(postId);
            } catch (Exception e) {
                log.error("批量删除Post失败, ID: {}", postId, e);
            }
        }
    }
}