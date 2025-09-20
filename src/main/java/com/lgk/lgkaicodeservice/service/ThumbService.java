package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandler;
import com.mybatisflex.core.service.IService;
import com.lgk.lgkaicodeservice.model.entity.Thumb;

/**
 * 点赞表 服务层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
public interface ThumbService extends IService<Thumb> {

    int doThumb(ThumbTypeEnum type, long targetId, User loginUser);

    int doThumbInner(ThumbTypeEnum type, long targetId, Long userId, ThumbHandler thumbHandler);
}
