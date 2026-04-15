package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 点赞处理器工厂
 * <p>
 * 统一管理所有类型的点赞处理器，负责根据类型获取对应的 Handler。
 * <p>
 * 扩展方式：
 * 1. 在 {@link ThumbTypeEnum} 中新增枚举值（如 IMAGE, VIDEO）
 * 2. 创建对应的 Handler 实现（如 ImageThumbHandler, VideoThumbHandler）
 * 3. 在 {@link #init()} 方法中注册即可
 *
 * @see ThumbHandler
 * @see ThumbTypeEnum
 */
@Component
public class ThumbHandlerFactory {

    /**
     * 使用 EnumMap 存储 Handler，key 为 ThumbTypeEnum
     */
    private final Map<ThumbTypeEnum, ThumbHandler> handlerMap = new EnumMap<>(ThumbTypeEnum.class);

    @Resource
    private PostThumbHandler postThumbHandler;
    // TODO: 后续扩展其他类型时，在此注入对应的 Handler
    // @Resource
    // private ImageThumbHandler imageThumbHandler;
    // @Resource
    // private VideoThumbHandler videoThumbHandler;

    @PostConstruct
    public void init() {
        // 注册所有已实现的 Handler
        registerHandler(postThumbHandler);
        // registerHandler(imageThumbHandler);
        // registerHandler(videoThumbHandler);
    }

    /**
     * 注册点赞处理器
     *
     * @param handler 点赞处理器
     */
    private void registerHandler(ThumbHandler handler) {
        handlerMap.put(handler.getType(), handler);
    }

    /**
     * 根据类型获取处理器
     *
     * @param type 点赞类型
     * @return 对应的处理器
     * @throws BusinessException 如果类型不支持
     */
    public ThumbHandler getHandler(ThumbTypeEnum type) {
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "点赞类型不能为空");
        }
        ThumbHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的点赞类型: " + type.getText());
        }
        return handler;
    }

    /**
     * 根据类型值获取处理器
     *
     * @param typeCode 类型值（对应数据库存储的值）
     * @return 对应的处理器
     * @throws BusinessException 如果类型值不支持
     */
    public ThumbHandler getHandlerByCode(Integer typeCode) {
        ThumbTypeEnum type = ThumbTypeEnum.getEnumByCode(typeCode);
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的点赞类型值: " + typeCode);
        }
        return getHandler(type);
    }
}
