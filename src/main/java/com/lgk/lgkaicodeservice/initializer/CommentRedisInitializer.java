package com.lgk.lgkaicodeservice.initializer;

import com.lgk.lgkaicodeservice.model.entity.Comment;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.CommentService;
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
 * 评论 Redis 数据初始化器
 * 应用启动时将现有评论数据同步到 Redis 存在性 Set，供评论点赞功能校验目标是否存在
 */
@Component
@Order(101) // 确保在其他组件初始化后执行
@Slf4j
public class CommentRedisInitializer implements ApplicationRunner {

    @Resource
    private CommentService commentService;

    @Resource
    private RedisLuaScriptService redisLuaScriptService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始增量同步评论数据到 Redis...");

        try {
            // 查询所有未删除的评论
            List<Comment> comments = commentService.list();

            // 获取数据库中有效评论 ID 集合（字符串形式，便于与 Redis Set 比较）
            Set<String> dbCommentIdSet = (comments == null || comments.isEmpty())
                    ? Set.of()
                    : comments.stream()
                            .map(comment -> comment.getId().toString())
                            .collect(Collectors.toSet());

            // 获取 Redis 中已存在的评论 ID
            Set<String> redisCommentIdSet = redisLuaScriptService.getExistsIds(ThumbTypeEnum.COMMENT);

            // ---- 1. 清理 Redis 中已被删除的评论 ----
            List<Long> idsToRemove = redisCommentIdSet.stream()
                    .filter(id -> !dbCommentIdSet.contains(id))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            if (!idsToRemove.isEmpty()) {
                redisLuaScriptService.batchRemoveFromExistsSet(ThumbTypeEnum.COMMENT, idsToRemove);
                log.info("已从 Redis 中清理 {} 个已删除评论", idsToRemove.size());
            }

            // ---- 2. 增量同步新增/缺失的评论 ----
            if (comments == null || comments.isEmpty()) {
                log.info("数据库中没有评论数据，跳过新增同步");
                return;
            }

            List<Long> commentIdsToSync = comments.stream()
                    .filter(comment -> !redisCommentIdSet.contains(comment.getId().toString()))
                    .map(Comment::getId)
                    .collect(Collectors.toList());

            if (commentIdsToSync.isEmpty()) {
                log.info("所有评论数据已在 Redis 中，无需新增同步");
                return;
            }

            // 批量添加到 Redis 存在性 Set
            redisLuaScriptService.batchAddToExistsSet(ThumbTypeEnum.COMMENT, commentIdsToSync);

            log.info("评论数据 Redis 增量同步完成: 数据库共 {} 条评论，本次新增同步 {} 条",
                    comments.size(), commentIdsToSync.size());

        } catch (Exception e) {
            log.error("评论 Redis 数据初始化失败", e);
            // 初始化失败不影响应用启动
        }
    }
}
