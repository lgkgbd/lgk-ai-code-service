package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.PostService;
import com.lgk.lgkaicodeservice.service.RedisLuaScriptService;
import com.lgk.lgkaicodeservice.utils.RedisKeyUtil;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 帖子点赞处理器
 * <p>
 * 实现 {@link ThumbHandler} 接口，处理帖子类型的点赞业务逻辑。
 * 如需扩展其他类型的点赞（如图片、视频），只需：
 * 1. 在 {@link ThumbTypeEnum} 中新增枚举值
 * 2. 创建新的 Handler 实现 {@link ThumbHandler} 接口
 * 3. 在 {@link ThumbHandlerFactory} 中注册即可
 *
 * @see ThumbHandler
 * @see ThumbHandlerFactory
 */
@Component
@Slf4j
public class PostThumbHandler implements ThumbHandler {

    @Resource
    private PostService postService;

    @Resource
    private RedisLuaScriptService redisLuaScriptService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public ThumbTypeEnum getType() {
        return ThumbTypeEnum.POST;
    }

    @Override
    public boolean checkTargetExists(Long targetId) {
        return postService.getById(targetId) != null;
    }

    @Override
    public boolean checkTargetExistsInRedis(Long targetId) {
        return redisLuaScriptService.checkTargetExists(getType(), targetId);
    }

    @Override
    public Integer incrementThumb(Long targetId) {
        // 使用原子操作更新数据库
        boolean result = UpdateChain.of(Post.class)
                .setRaw("thumbNum", "thumbNum + 1")
                .where("id = ?", targetId)
                .update();

        return result ? 1 : 0;
    }

    @Override
    public Integer decrementThumb(Long targetId) {
        boolean result = UpdateChain.of(Post.class)
                .setRaw("thumbNum", "thumbNum - 1")
                .where("id = ?", targetId)
                .update();

        return result ? -1 : 0;
    }

    @Override
    public void incrementThumbBatch(Map<Long, Long> targetCountMap) {
        if (targetCountMap == null || targetCountMap.isEmpty()) {
            return;
        }
        // 拼接批量 UPDATE：UPDATE post SET thumbNum = thumbNum + N WHERE id = ?
        StringBuilder sql = new StringBuilder("UPDATE post SET thumbNum = CASE id ");
        for (Map.Entry<Long, Long> entry : targetCountMap.entrySet()) {
            sql.append("WHEN ").append(entry.getKey()).append(" THEN thumbNum + ").append(entry.getValue()).append(" ");
        }
        sql.append("END WHERE id IN (");
        sql.append(targetCountMap.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ")));
        sql.append(")");

        jdbcTemplate.execute(sql.toString());
        log.debug("批量点赞聚合更新，影响 {} 条记录: {}", targetCountMap.size(), targetCountMap.keySet());
    }

    @Override
    public void decrementThumbBatch(List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        String sql = "UPDATE post SET thumbNum = thumbNum - 1 WHERE id IN (" +
                targetIds.stream().map(String::valueOf).collect(Collectors.joining(", ")) +
                ")";
        jdbcTemplate.execute(sql);
        log.debug("批量取消点赞聚合更新，影响 {} 条记录: {}", targetIds.size(), targetIds);
    }

    @Override
    public Integer incrementThumbInRedis(Long targetId) {
        // 仅更新 Redis 点赞数，不操作数据库（由批量同步任务处理）
        try {
            String key = RedisKeyUtil.getCountKey(getType(), targetId);
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            counter.incrementAndGet();
            return 1;
        } catch (Exception e) {
            // Redis 失败时回退到数据库更新
            return incrementThumb(targetId);
        }
    }

    @Override
    public Integer decrementThumbInRedis(Long targetId) {
        // 仅更新 Redis 点赞数，不操作数据库（由批量同步任务处理）
        try {
            String key = RedisKeyUtil.getCountKey(getType(), targetId);
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            counter.decrementAndGet();
            return -1;
        } catch (Exception e) {
            // Redis 失败时回退到数据库更新
            return decrementThumb(targetId);
        }
    }
}
