package com.lgk.lgkaicodeservice.initializer;

import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import com.lgk.lgkaicodeservice.service.RedisLuaScriptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子 Redis 数据初始化器
 * 应用启动时将现有帖子数据同步到 Redis
 */
@Component
@Order(100) // 确保在其他组件初始化后执行
@Slf4j
public class PostRedisInitializer implements ApplicationRunner {

    @Resource
    private PostService postService;

    @Resource
    private RedisLuaScriptService redisLuaScriptService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始增量同步帖子数据到 Redis...");
        
        try {
            // 查询所有未删除的帖子
            List<Post> posts = postService.list();

            // 获取数据库中有效帖子 ID 集合（字符串形式，便于与 Redis Set 比较）
            Set<String> dbPostIdSet = (posts == null || posts.isEmpty())
                    ? Set.of()
                    : posts.stream()
                            .map(post -> post.getId().toString())
                            .collect(Collectors.toSet());

            // 获取 Redis 中已存在的帖子 ID
            Set<String> redisPostIdSet = redisLuaScriptService.getExistsPostIds();

            // ---- 1. 清理 Redis 中已被删除的帖子 ----
            List<Long> idsToRemove = redisPostIdSet.stream()
                    .filter(id -> !dbPostIdSet.contains(id))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            if (!idsToRemove.isEmpty()) {
                redisLuaScriptService.batchRemovePostsFromExistsSet(idsToRemove);
                log.info("已从 Redis 中清理 {} 个已删除帖子", idsToRemove.size());
            }

            // ---- 2. 增量同步新增/缺失的帖子 ----
            if (posts == null || posts.isEmpty()) {
                log.info("数据库中没有帖子数据，跳过新增同步");
                return;
            }

            List<Post> postsToSync = posts.stream()
                    .filter(post -> !redisPostIdSet.contains(post.getId().toString()))
                    .collect(Collectors.toList());

            if (postsToSync.isEmpty()) {
                log.info("所有帖子数据已在 Redis 中，无需新增同步");
                return;
            }

            // 批量添加到 Redis 存在性 Set
            List<Long> postIdsToSync = postsToSync.stream()
                    .map(Post::getId)
                    .collect(Collectors.toList());
            redisLuaScriptService.batchAddPostsToExistsSet(postIdsToSync);

            // 初始化每个帖子的点赞数
            int count = 0;
            for (Post post : postsToSync) {
                try {
                    redisLuaScriptService.initPostThumbCount(
                        post.getId(), 
                        post.getThumbNum() != null ? post.getThumbNum() : 0
                    );
                    count++;
                } catch (Exception e) {
                    log.error("初始化帖子点赞数失败: postId={}", post.getId(), e);
                }
            }

            log.info("帖子数据 Redis 增量同步完成: 数据库共 {} 个帖子，本次新增同步 {} 个，成功 {} 个",
                    posts.size(), postsToSync.size(), count);
                    
        } catch (Exception e) {
            log.error("帖子 Redis 数据初始化失败", e);
            // 初始化失败不影响应用启动
        }
    }
}
