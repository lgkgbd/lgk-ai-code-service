package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ThumbHandlerFactory {
    private final Map<Integer, ThumbHandler> handlerMap = new HashMap<>();

    @Resource
    private PostThumbHandler postThumbHandler;

    @PostConstruct
    public void init() {
        handlerMap.put(ThumbTypeEnum.POST.getValue(), postThumbHandler);
    }

    public ThumbHandler getHandler(ThumbTypeEnum type) {
        ThumbHandler handler = handlerMap.get(type.getValue());
        if (handler == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"Unsupported like type: " + type);
        }
        return handler;
    }
}
