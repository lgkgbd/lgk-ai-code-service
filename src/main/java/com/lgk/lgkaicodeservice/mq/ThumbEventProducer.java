package com.lgk.lgkaicodeservice.mq;

import com.lgk.lgkaicodeservice.config.RabbitMQConfig;
import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.model.dto.thumb.TempThumbDTO;
import com.lgk.lgkaicodeservice.model.mq.ThumbEvent;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.service.TempThumbStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 点赞事件消息生产者
 * <p>
 * 负责将点赞/取消点赞事件发送到 RabbitMQ。
 * 发送失败时降级到 TempThumbStorageService（兜底），保证不丢消息。
 */
@Component
@Slf4j
public class ThumbEventProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private TempThumbStorageService tempThumbStorageService;

    /**
     * 发送点赞事件（统一队列，点赞和取消点赞都走这里）
     *
     * @param userId    用户 ID
     * @param type      点赞类型
     * @param targetId  目标 ID
     * @param thumbId   点赞记录 ID（取消点赞时为 null）
     * @param operation 操作类型：1=点赞，0=取消点赞
     * @return 是否发送成功，false 时已降级写 TempThumbStorageService
     */
    public boolean sendEvent(Long userId, ThumbTypeEnum type, Long targetId, Long thumbId, Integer operation) {
        ThumbEvent event = buildEvent(userId, type, targetId, thumbId, operation);
        return doSend(event, RabbitMQConfig.THUMB_EVENT_ROUTING_KEY, () ->
                tempThumbStorageService.saveTempThumb(userId, type, targetId, thumbId, operation)
        );
    }

    /**
     * 构建事件对象
     */
    private ThumbEvent buildEvent(Long userId, ThumbTypeEnum type, Long targetId,
                                   Long thumbId, Integer operation) {
        ThumbEvent event = new ThumbEvent();
        event.setMsgId(UUID.randomUUID().toString());
        event.setUserId(userId);
        event.setType(type.getCode());
        event.setTargetId(targetId);
        event.setThumbId(thumbId);
        event.setOperation(operation);
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    /**
     * 发送消息，失败时执行降级逻辑
     *
     * @param event        事件对象
     * @param routingKey   路由 key
     * @param fallbackFunc 降级回调（写 TempThumbStorageService）
     * @return 是否发送成功
     */
    private boolean doSend(ThumbEvent event, String routingKey, Runnable fallbackFunc) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.THUMB_EXCHANGE,
                    routingKey,
                    event
            );
            log.debug("点赞事件发送成功: msgId={}, userId={}, targetId={}, operation={}",
                    event.getMsgId(), event.getUserId(), event.getTargetId(), event.getOperation());
            return true;
        } catch (Exception e) {
            log.error("点赞事件发送失败，降级到 TempThumbStorageService: msgId={}, userId={}, targetId={}",
                    event.getMsgId(), event.getUserId(), event.getTargetId(), e);
            // 降级：写 TempThumbStorageService
            fallbackFunc.run();
            return false;
        }
    }
}
