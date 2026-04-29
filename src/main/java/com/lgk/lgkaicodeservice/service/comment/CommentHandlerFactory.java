package com.lgk.lgkaicodeservice.service.comment;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.enums.CommentTargetTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CommentHandlerFactory {

    private final Map<Integer, CommentHandler> handlerMap = new HashMap<>();

    @Resource
    private PostCommentHandler postCommentHandler;

    @PostConstruct
    public void init() {
        handlerMap.put(CommentTargetTypeEnum.POST.getValue(), postCommentHandler);
    }

    public CommentHandler getHandler(CommentTargetTypeEnum type) {
        CommentHandler handler = handlerMap.get(type.getValue());
        if (handler == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的评论目标类型: " + type);
        }
        return handler;
    }
}
