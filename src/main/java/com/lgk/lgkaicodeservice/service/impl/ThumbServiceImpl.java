package com.lgk.lgkaicodeservice.service.impl;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandler;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandlerFactory;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgk.lgkaicodeservice.model.entity.Thumb;
import com.lgk.lgkaicodeservice.mapper.ThumbMapper;
import com.lgk.lgkaicodeservice.service.ThumbService;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

/**
 * 点赞表 服务层实现。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>  implements ThumbService{

    @Resource
    private ThumbHandlerFactory thumbHandlerFactory;

    @Override
    public int doThumb(ThumbTypeEnum type, long targetId, User loginUser) {

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
    public int doThumbInner(ThumbTypeEnum type, long targetId, Long userId, ThumbHandler thumbHandler) {
        QueryWrapper thumbQueryWrapper = new QueryWrapper();
        thumbQueryWrapper.eq("type", type.getValue());
        thumbQueryWrapper.eq("target_id", targetId);
        thumbQueryWrapper.eq("user_id", userId);
        Thumb oldPostThumb = this.getOne(thumbQueryWrapper);
        boolean result;
        // 已点赞
        if (oldPostThumb != null) {
            result = this.remove(thumbQueryWrapper);
            if (result) {
                // 点赞数 - 1
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
                // 点赞数 + 1
                return thumbHandler.incrementThumb(targetId);
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
    }
}
