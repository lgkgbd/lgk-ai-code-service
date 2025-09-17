package com.lgk.lgkaicodeservice.datasync;

import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Post数据同步测试
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@SpringBootTest
@Slf4j
public class PostDataSyncTest {

    @Resource
    private PostService postService;

    @Resource
    private PostDataSyncService postDataSyncService;

    /**
     * 测试全量同步
     */
    @Test
    public void testSyncAllPosts() {
        try {
            long count = postDataSyncService.syncAllPostsToEs();
            log.info("全量同步完成，同步数据条数: {}", count);
        } catch (Exception e) {
            log.error("全量同步测试失败", e);
        }
    }

    /**
     * 测试创建Post并同步
     */
    @Test
    public void testCreateAndSyncPost() {
        try {
            // 创建测试Post
            Post post = new Post();
            post.setTitle("Canal同步测试帖子");
            post.setContent("这是一个用于测试Canal数据同步功能的帖子内容。");
            post.setTags("[\"测试\", \"Canal\", \"同步\"]");
            post.setUserId(1L);
            post.setThumbNum(0);
            post.setFavourNum(0);
            post.setViewNum(0);
            post.setPriority(0);
            post.setCreateTime(LocalDateTime.now());
            post.setUpdateTime(LocalDateTime.now());

            // 保存到数据库（这会触发Canal同步）
            boolean saved = postService.save(post);
            log.info("Post创建结果: {}, ID: {}", saved, post.getId());

            // 等待一段时间让Canal处理
            Thread.sleep(2000);

            // 手动同步验证
            boolean syncResult = postDataSyncService.syncPostToEs(post.getId());
            log.info("手动同步结果: {}", syncResult);

        } catch (Exception e) {
            log.error("创建和同步Post测试失败", e);
        }
    }

    /**
     * 测试更新Post并同步
     */
    @Test
    public void testUpdateAndSyncPost() {
        try {
            // 假设存在ID为1的Post
            Long postId = 325925202496081920L;
            Post post = postService.getById(postId);
            
            if (post != null) {
                post.setTitle("更新后的标题 - " + System.currentTimeMillis());
                post.setContent("更新后的内容 - " + LocalDateTime.now());
                post.setUpdateTime(LocalDateTime.now());

                // 更新到数据库（这会触发Canal同步）
                boolean updated = postService.updateById(post);
                log.info("Post更新结果: {}", updated);

                // 等待一段时间让Canal处理
                Thread.sleep(2000);

                // 手动同步验证
                boolean syncResult = postDataSyncService.syncPostToEs(postId);
                log.info("手动同步结果: {}", syncResult);
            } else {
                log.warn("未找到ID为{}的Post", postId);
            }

        } catch (Exception e) {
            log.error("更新和同步Post测试失败", e);
        }
    }

    /**
     * 测试删除Post并同步
     */
    @Test
    public void testDeleteAndSyncPost() {
        try {
            // 创建一个测试Post用于删除
            Post post = new Post();
            post.setTitle("待删除的测试帖子");
            post.setContent("这个帖子将被删除以测试同步功能。");
            post.setUserId(1L);
            post.setThumbNum(0);
            post.setFavourNum(0);
            post.setViewNum(0);
            post.setPriority(0);
            post.setCreateTime(LocalDateTime.now());
            post.setUpdateTime(LocalDateTime.now());

            postService.save(post);
            log.info("创建待删除Post，ID: {}", post.getId());

            // 等待同步
            Thread.sleep(1000);

            // 逻辑删除（这会触发Canal同步）
            boolean deleted = postService.removeById(post.getId());
            log.info("Post逻辑删除结果: {}", deleted);

            // 等待一段时间让Canal处理
            Thread.sleep(2000);

            log.info("删除和同步测试完成");

        } catch (Exception e) {
            log.error("删除和同步Post测试失败", e);
        }
    }
}