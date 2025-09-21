package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.mapper.ThumbMapper;
import com.lgk.lgkaicodeservice.model.entity.Thumb;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.ThumbService;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandler;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandlerFactory;
import com.lgk.lgkaicodeservice.utils.RedisKeyUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞表 服务层实现。
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

    @Override
    public int doThumb(ThumbTypeEnum type, Long targetId, User loginUser) {

        ThumbHandler thumbHandler = thumbHandlerFactory.getHandler(type);
        boolean isExist = thumbHandler.checkTargetExists(targetId);
        if (!isExist) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        Long userId = loginUser.getId();

        // 每个用户串行点赞
        // 锁必须要包裹住事务方法
        ThumbService thumbService = (ThumbService) AopContext.currentProxy();
        synchronized (String.valueOf(userId).intern()) {
            return thumbService.doThumbInner(type, targetId, userId, thumbHandler);
        }
    }

    @Override
    public Boolean hasThumb(ThumbTypeEnum type, Long targetId, Long userId) {
        String key = RedisKeyUtil.getUserThumbKey(userId,type);
        return redissonClient.getMap(key).containsKey(targetId.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class) //声明式事务管理的注解
    public int doThumbInner(ThumbTypeEnum type, Long targetId, Long userId, ThumbHandler thumbHandler) {
        QueryWrapper thumbQueryWrapper = new QueryWrapper();
        thumbQueryWrapper.eq("type", type.getValue());
        thumbQueryWrapper.eq("targetId", targetId);
        thumbQueryWrapper.eq("userId", userId);
//        Thumb oldPostThumb = this.getOne(thumbQueryWrapper);
        // 改成从 redis 中检查是否存在
        Boolean exist = this.hasThumb(type, targetId, userId);

        boolean result;
        // 已点赞
        if (exist) {
            result = this.remove(thumbQueryWrapper);
            if (result) {
                // 点赞数 - 1
                //TODO 从 redis 中删除
                String key = RedisKeyUtil.getUserThumbKey(userId,type);
                redissonClient.getMap(key).remove(targetId.toString());

                return thumbHandler.decrementThumb(targetId);
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        } else {
            // 未点赞
            Thumb thumb = new Thumb();
            thumb.setType(type.getValue());
            thumb.setTargetId(targetId);
            thumb.setUserId(userId);
            result = this.save(thumb);
            if (result) {
                //TODO 写入 redis
                String key = RedisKeyUtil.getUserThumbKey(userId,type);
                redissonClient.getMap(key).put(targetId.toString(),thumb.getId());

                // 点赞数 + 1
                return thumbHandler.incrementThumb(targetId);
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
    }


    private String getTimeSlice(){
        DateTime nowDate = DateUtil.date();
        // 获取当前时间的整数秒
        return DateUtil.format(nowDate,"HH:mm") + (DateUtil.second(nowDate)/10) * 10;
    }

}
