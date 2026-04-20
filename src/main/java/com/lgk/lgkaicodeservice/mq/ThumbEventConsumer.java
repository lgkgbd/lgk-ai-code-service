package com.lgk.lgkaicodeservice.mq;

import com.lgk.lgkaicodeservice.job.ThumbBatchSyncJob;
import com.lgk.lgkaicodeservice.model.dto.thumb.TempThumbDTO;
import com.lgk.lgkaicodeservice.model.mq.ThumbEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 点赞事件消息消费者
 * <p>
 * 监听 RabbitMQ 点赞队列，批量消费消息并写入数据库。
 * 使用 thumbBatchContainerFactory 实现真正的批量消费：
 * Spring AMQP 容器将多条消息攒批后一次性投递给 List<ThumbEvent> 参数方法。
 * <p>
 * 去重策略：同一批次内，同一用户对同一目标，保留最后一条操作（防止快速连续点击）。
 * 消费逻辑复用 ThumbBatchSyncJob 的处理方法，保证主路径和降级路径逻辑一致。
 */
@Component
@Slf4j
public class ThumbEventConsumer {

    @Resource
    private ThumbBatchSyncJob thumbBatchSyncJob;

    /**
     * 批量消费点赞事件（点赞和取消点赞都走这里）
     * <p>
     * 容器会根据 prefetchCount 和 receiveTimeout 攒批：
     * - 高流量时：最多攒 100 条一批处理
     * - 低流量时：10秒超时后即使只有 1 条也会触发处理
     * <p>
     * 注意：Spring AMQP 批量模式下，每条 MQ 消息是独立的 JSON 对象（由生产者逐条发送），
     * 容器负责将多条 Message 聚合为 List 后调用此方法。
     */
    @RabbitListener(queues = "thumb.event.queue", containerFactory = "thumbBatchContainerFactory")
    public void consumeBatch(List<ThumbEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        processBatch(events);
        log.info("点赞事件批量消费完成，共 {} 条消息", events.size());
    }

    /**
     * 统一处理批量消息
     * <p>
     * 去重策略：同一批次内，同一用户 + 同一目标，保留最后一条操作。
     * 例：thumb → unthumb → thumb → 保留最后一个 thumb
     */
    private void processBatch(List<ThumbEvent> events) {
        try {
            // 1. 转换为 TempThumbDTO（方便复用 Job 的处理逻辑）
            List<TempThumbDTO> dtos = events.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            // 2. 跨操作去重：同一 userId + targetId + type，保留最后一条
            Map<String, TempThumbDTO> dedupMap = new LinkedHashMap<>();
            for (TempThumbDTO dto : dtos) {
                String key = dto.getUserId() + ":" + dto.getTargetId() + ":" + dto.getType();
                dedupMap.put(key, dto); // 后面的覆盖前面的，自然保留最后一条
            }
            List<TempThumbDTO> deduped = new ArrayList<>(dedupMap.values());

            // 3. 按操作类型分组
            Map<Integer, List<TempThumbDTO>> groups = deduped.stream()
                    .collect(Collectors.groupingBy(TempThumbDTO::getOperation));

            List<TempThumbDTO> thumbRecords = groups.getOrDefault(1, Collections.emptyList());
            List<TempThumbDTO> unthumbRecords = groups.getOrDefault(0, Collections.emptyList());

            // 4. 调用 Job 处理（复用已有逻辑）
            if (!thumbRecords.isEmpty()) {
                thumbBatchSyncJob.processThumbOperations(thumbRecords);
            }
            if (!unthumbRecords.isEmpty()) {
                thumbBatchSyncJob.processUnthumbOperations(unthumbRecords);
            }

            log.debug("批量处理完成：总消息 {} → 去重 {}（点赞: {}, 取消: {}）",
                    events.size(), deduped.size(), thumbRecords.size(), unthumbRecords.size());

        } catch (Exception e) {
            log.error("批量消费点赞事件失败", e);
            // 异常会触发 RabbitMQ 重试，失败超过阈值后进入死信队列
            throw new RuntimeException("批量消费点赞事件失败", e);
        }
    }

    /**
     * ThumbEvent 转换为 TempThumbDTO
     * <p>
     * ThumbEvent 复用了 TempThumbDTO 的核心字段，转换后可直接复用 Job 处理逻辑。
     * timeSlice 使用固定值 "MQ"，因为 MQ 消息不走 TempThumbStorageService，无需时间片标记。
     */
    private TempThumbDTO convertToDTO(ThumbEvent event) {
        TempThumbDTO dto = new TempThumbDTO();
        dto.setThumbId(event.getThumbId());
        dto.setUserId(event.getUserId());
        dto.setType(event.getType());
        dto.setTargetId(event.getTargetId());
        dto.setOperation(event.getOperation());
        dto.setCreateTime(event.getCreateTime());
        dto.setTimeSlice("MQ"); // MQ 消息不走 TempThumbStorageService，用固定值标记
        return dto;
    }
}
