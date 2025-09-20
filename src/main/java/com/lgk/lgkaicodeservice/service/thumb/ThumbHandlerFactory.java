package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ThumbHandlerFactory {
    private final Map<Integer, ThumbHandler> handlerMap = new HashMap<>();

    public ThumbHandlerFactory() {
        handlerMap.put(ThumbTypeEnum.POST.getValue(), new PostThumbHandler());
    }

    public ThumbHandler getHandler(ThumbTypeEnum type) {
        ThumbHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"Unsupported like type: " + type);
        }
        return handler;
    }
}
