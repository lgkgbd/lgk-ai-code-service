package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.constant.RedisLuaScriptConstant;
import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.utils.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis Lua 脚本服务
 * 用于执行原子性的点赞操作
 * <p>
 * 所有 Key 格式统一使用 {@link RedisKeyUtil} 生成，支持多类型扩展。
 *
 * @see RedisLuaScriptConstant
 * @see RedisKeyUtil
 */
@Service
@Slf4j
public class RedisLuaScriptService {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 执行点赞操作（使用 Lua 脚本保证原子性）
     *
     * @param userId   用户ID
     * @param type     点赞类型
     * @param targetId 目标ID
     * @param thumbId  点赞记录ID
     * @return 操作结果码：1成功，-1目标不存在，-2已点赞
     */
    public Long doThumb(Long userId, ThumbTypeEnum type, Long targetId, Long thumbId) {
        try {
            List<Object> keys = Arrays.asList(
                RedisKeyUtil.getExistsKey(type),          // thumb:exists:{type}
                RedisKeyUtil.getUserThumbKey(userId, type), // thumb:user:{userId}:{type}
                RedisKeyUtil.getCountKey(type, targetId)   // thumb:count:{type}:{targetId}
            );
            List<Object> args = Arrays.asList(
                targetId.toString(),
                thumbId.toString()
            );

            RScript script = redissonClient.getScript();
            return script.eval(
                RScript.Mode.READ_WRITE,
                RedisLuaScriptConstant.THUMB_DO_LUA,
                RScript.ReturnType.INTEGER,
                keys,
                args.toArray()
            );
        } catch (Exception e) {
            log.error("执行点赞 Lua 脚本失败: userId={}, type={}, targetId={}", userId, type, targetId, e);
            throw new RuntimeException("点赞操作失败", e);
        }
    }

    /**
     * 执行取消点赞操作（使用 Lua 脚本保证原子性）
     *
     * @param userId   用户ID
     * @param type     点赞类型
     * @param targetId 目标ID
     * @return 操作结果码：1成功，-1未点赞
     */
    public Long undoThumb(Long userId, ThumbTypeEnum type, Long targetId) {
        try {
            List<Object> keys = Arrays.asList(
                RedisKeyUtil.getUserThumbKey(userId, type), // thumb:user:{userId}:{type}
                RedisKeyUtil.getCountKey(type, targetId)    // thumb:count:{type}:{targetId}
            );
            List<Object> args = Collections.singletonList(targetId.toString());

            RScript script = redissonClient.getScript();
            return script.eval(
                RScript.Mode.READ_WRITE,
                RedisLuaScriptConstant.THUMB_UNDO_LUA,
                RScript.ReturnType.INTEGER,
                keys,
                args.toArray()
            );
        } catch (Exception e) {
            log.error("执行取消点赞 Lua 脚本失败: userId={}, type={}, targetId={}", userId, type, targetId, e);
            throw new RuntimeException("取消点赞操作失败", e);
        }
    }

    /**
     * 检查目标是否存在（使用 Lua 脚本）
     *
     * @param type     点赞类型
     * @param targetId 目标ID
     * @return true: 存在，false: 不存在
     */
    public boolean checkTargetExists(ThumbTypeEnum type, Long targetId) {
        try {
            List<Object> keys = Collections.singletonList(RedisKeyUtil.getExistsKey(type));
            List<Object> args = Collections.singletonList(targetId.toString());

            RScript script = redissonClient.getScript();
            Long result = script.eval(
                RScript.Mode.READ_ONLY,
                RedisLuaScriptConstant.EXISTS_LUA,
                RScript.ReturnType.INTEGER,
                keys,
                args.toArray()
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("检查目标存在性 Lua 脚本失败: type={}, targetId={}", type, targetId, e);
            throw new RuntimeException("检查目标存在性失败", e);
        }
    }

    /**
     * 添加目标到存在性 Set（创建目标时调用）
     *
     * @param type     点赞类型
     * @param targetId 目标ID
     */
    public void addToExistsSet(ThumbTypeEnum type, Long targetId) {
        try {
            redissonClient.getSet(RedisKeyUtil.getExistsKey(type)).add(targetId.toString());
        } catch (Exception e) {
            log.error("添加目标到存在性 Set 失败: type={}, targetId={}", type, targetId, e);
            throw new RuntimeException("添加目标到 Redis 失败", e);
        }
    }

    /**
     * 从存在性 Set 中移除目标，并清理对应的点赞数 Key（删除目标时调用）
     *
     * @param type     点赞类型
     * @param targetId 目标ID
     */
    public void removeFromExistsSet(ThumbTypeEnum type, Long targetId) {
        try {
            redissonClient.getSet(RedisKeyUtil.getExistsKey(type)).remove(targetId.toString());
            redissonClient.getBucket(RedisKeyUtil.getCountKey(type, targetId)).delete();
        } catch (Exception e) {
            log.error("从存在性 Set 移除目标失败: type={}, targetId={}", type, targetId, e);
            throw new RuntimeException("从 Redis 移除目标失败", e);
        }
    }

    /**
     * 批量添加目标到存在性 Set（初始化时使用）
     *
     * @param type     点赞类型
     * @param targetIds 目标ID列表
     */
    public void batchAddToExistsSet(ThumbTypeEnum type, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        try {
            RSet<String> set = redissonClient.getSet(RedisKeyUtil.getExistsKey(type));
            for (Long targetId : targetIds) {
                set.add(targetId.toString());
            }
            log.info("批量添加 {} 个目标到存在性 Set: type={}", targetIds.size(), type);
        } catch (Exception e) {
            log.error("批量添加目标到存在性 Set 失败: type={}", type, e);
            throw new RuntimeException("批量添加目标到 Redis 失败", e);
        }
    }

    /**
     * 获取目标点赞数
     *
     * @param type     点赞类型
     * @param targetId 目标ID
     * @return 点赞数
     */
    public Long getThumbCount(ThumbTypeEnum type, Long targetId) {
        try {
            String key = RedisKeyUtil.getCountKey(type, targetId);
            return redissonClient.getAtomicLong(key).get();
        } catch (Exception e) {
            log.error("获取目标点赞数失败: type={}, targetId={}", type, targetId, e);
            return 0L;
        }
    }

    /**
     * 初始化目标点赞数（从数据库同步时使用）
     *
     * @param type      点赞类型
     * @param targetId  目标ID
     * @param thumbNum  点赞数
     */
    public void initThumbCount(ThumbTypeEnum type, Long targetId, long thumbNum) {
        try {
            String key = RedisKeyUtil.getCountKey(type, targetId);
            redissonClient.getAtomicLong(key).set(thumbNum);
        } catch (Exception e) {
            log.error("初始化目标点赞数失败: type={}, targetId={}, thumbNum={}", type, targetId, thumbNum, e);
        }
    }

    /**
     * 批量从存在性 Set 中移除目标，并清理对应的点赞数 Key（初始化时清理已删除目标使用）
     *
     * @param type     点赞类型
     * @param targetIds 需要移除的目标 ID 列表
     */
    public void batchRemoveFromExistsSet(ThumbTypeEnum type, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        try {
            RSet<String> set = redissonClient.getSet(RedisKeyUtil.getExistsKey(type));
            for (Long targetId : targetIds) {
                set.remove(targetId.toString());
                // 同时清理该目标的点赞数 Key
                redissonClient.getAtomicLong(RedisKeyUtil.getCountKey(type, targetId)).delete();
            }
            log.info("批量从存在性 Set 移除 {} 个已删除目标: type={}", targetIds.size(), type);
        } catch (Exception e) {
            log.error("批量从存在性 Set 移除目标失败: type={}", type, e);
            throw new RuntimeException("批量从 Redis 移除目标失败", e);
        }
    }

    /**
     * 获取 Redis 中已存在的目标 ID 集合
     *
     * @param type 点赞类型
     * @return 目标 ID 字符串集合
     */
    public Set<String> getExistsIds(ThumbTypeEnum type) {
        try {
            RSet<String> set = redissonClient.getSet(RedisKeyUtil.getExistsKey(type));
            return new HashSet<>(set);
        } catch (Exception e) {
            log.error("获取 Redis 中已存在的目标 ID 失败: type={}", type, e);
            return Collections.emptySet();
        }
    }

    // ==================== 向后兼容方法（已废弃，推荐使用新方法） ====================

    /**
     * @deprecated 使用 {@link #checkTargetExists(ThumbTypeEnum, Long)} 代替
     */
    @Deprecated
    public boolean checkPostExists(Long targetId) {
        return checkTargetExists(ThumbTypeEnum.POST, targetId);
    }

    /**
     * @deprecated 使用 {@link #addToExistsSet(ThumbTypeEnum, Long)} 代替
     */
    @Deprecated
    public void addPostToExistsSet(Long postId) {
        addToExistsSet(ThumbTypeEnum.POST, postId);
    }

    /**
     * @deprecated 使用 {@link #removeFromExistsSet(ThumbTypeEnum, Long)} 代替
     */
    @Deprecated
    public void removePostFromExistsSet(Long postId) {
        removeFromExistsSet(ThumbTypeEnum.POST, postId);
    }

    /**
     * @deprecated 使用 {@link #batchAddToExistsSet(ThumbTypeEnum, List)} 代替
     */
    @Deprecated
    public void batchAddPostsToExistsSet(List<Long> postIds) {
        batchAddToExistsSet(ThumbTypeEnum.POST, postIds);
    }

    /**
     * @deprecated 使用 {@link #getThumbCount(ThumbTypeEnum, Long)} 代替
     */
    @Deprecated
    public Long getPostThumbCount(Long postId) {
        return getThumbCount(ThumbTypeEnum.POST, postId);
    }

    /**
     * @deprecated 使用 {@link #initThumbCount(ThumbTypeEnum, Long, long)} 代替
     */
    @Deprecated
    public void initPostThumbCount(Long postId, long thumbNum) {
        initThumbCount(ThumbTypeEnum.POST, postId, thumbNum);
    }

    /**
     * @deprecated 使用 {@link #batchRemoveFromExistsSet(ThumbTypeEnum, List)} 代替
     */
    @Deprecated
    public void batchRemovePostsFromExistsSet(List<Long> postIds) {
        batchRemoveFromExistsSet(ThumbTypeEnum.POST, postIds);
    }

    /**
     * @deprecated 使用 {@link #getExistsIds(ThumbTypeEnum)} 代替
     */
    @Deprecated
    public Set<String> getExistsPostIds() {
        return getExistsIds(ThumbTypeEnum.POST);
    }
}
