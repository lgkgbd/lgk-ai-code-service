package com.lgk.lgkaicodeservice.service.impl;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.mapper.ThumbMapper;
import com.lgk.lgkaicodeservice.model.entity.Thumb;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.mq.ThumbEventProducer;
import com.lgk.lgkaicodeservice.service.RedisLuaScriptService;
import com.lgk.lgkaicodeservice.service.ThumbService;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandler;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandlerFactory;
import com.lgk.lgkaicodeservice.utils.RedisKeyUtil;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 点赞表 服务层实现（基于 Redis + Lua 脚本）。
 * 使用 Lua 脚本保证点赞操作的原子性，避免并发问题。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>  implements ThumbService{

    @Resource
    private ThumbHandlerFactory thumbHandlerFactory;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisLuaScriptService redisLuaScriptService;

    @Resource
    private ThumbEventProducer thumbEventProducer;

    @Override
    public int doThumb(ThumbTypeEnum type, Long targetId, User loginUser) {
        ThumbHandler thumbHandler = thumbHandlerFactory.getHandler(type);
        
        // 使用 Redis 检查目标是否存在（高性能）
        boolean isExist = thumbHandler.checkTargetExistsInRedis(targetId);
        if (!isExist) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, targetId + " 不存在");
        }

        Long userId = loginUser.getId();

        // 使用 Redisson 分布式锁保证每个用户串行点赞
        String lockKey = RedisKeyUtil.getUserLockKey(userId);
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 3 秒，锁持有时间 10 秒
            boolean isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作过于频繁，请稍后再试");
            }
            
            try {
                return doThumbInner(type, targetId, userId, thumbHandler);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作被中断");
        }
    }

    @Override
    public Boolean hasThumb(ThumbTypeEnum type, Long targetId, Long userId) {
        String key = RedisKeyUtil.getUserThumbKey(userId,type);
        return redissonClient.getMap(key).containsKey(targetId.toString());
    }

    @Override
    public int doThumbInner(ThumbTypeEnum type, Long targetId, Long userId, ThumbHandler thumbHandler) {
        // 先从 Redis 检查是否已点赞
        Boolean exist = this.hasThumb(type, targetId, userId);

        if (exist) {
            // 已点赞，执行取消点赞
            return doUnthumb(type, targetId, userId, thumbHandler);
        } else {
            // 未点赞，执行点赞
            return doThumbWithTempStorage(type, targetId, userId, thumbHandler);
        }
    }

    /**
     * 使用临时存储执行点赞操作（先写 Redis，后批量同步到数据库）
     */
    private int doThumbWithTempStorage(ThumbTypeEnum type, Long targetId, Long userId, ThumbHandler thumbHandler) {
        // 生成临时 thumbId（用于 Redis 存储，后续同步到数据库时会重新生成或使用）
        Long tempThumbId = System.currentTimeMillis();

        // 使用 Lua 脚本原子性更新 Redis
        // 注意：Lua 脚本内部已经执行了 HSET（记录点赞）和 INCR（更新计数），无需再单独更新计数
        Long result = redisLuaScriptService.doThumb(userId, type, targetId, tempThumbId);

        if (result == -1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, targetId + " 不存在");
        }

        if (result == -2) {
            // 已经点赞过了（并发情况）
            log.warn("并发点赞冲突: userId={}, targetId={}", userId, targetId);
            return 0;
        }

        if (result == 1) {
            // 点赞成功，发送到 RabbitMQ（失败时降级到 TempThumbStorageService）
            // Redis 计数已由 Lua 脚本原子性更新，无需再次调用 incrementThumbInRedis
            thumbEventProducer.sendEvent(userId, type, targetId, tempThumbId, 1);
            return 1;
        }

        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "点赞操作失败");
    }

    /**
     * 使用临时存储执行取消点赞操作
     */
    private int doUnthumb(ThumbTypeEnum type, Long targetId, Long userId, ThumbHandler thumbHandler) {
        // 使用 Lua 脚本原子性更新 Redis（同时删除用户点赞 Hash 并减少计数）
        Long result = redisLuaScriptService.undoThumb(userId, type, targetId);

        if (result == -1) {
            // Redis 中没有点赞记录，说明该记录已同步到数据库但 Redis Hash 已过期/被清理
            // 此时应直接操作数据库，避免临时存储中出现不一致记录
            log.warn("Redis 中无点赞记录，直接操作数据库: userId={}, targetId={}", userId, targetId);
            return thumbHandler.decrementThumb(targetId);
        }

        // result == 1：Lua 脚本已原子性删除 Hash 并 DECR 计数，发送到 RabbitMQ
        // 失败时降级到 TempThumbStorageService，由 ThumbBatchSyncJob 兜底处理
        thumbEventProducer.sendEvent(userId, type, targetId, null, 0);

        return -1;
    }

}
