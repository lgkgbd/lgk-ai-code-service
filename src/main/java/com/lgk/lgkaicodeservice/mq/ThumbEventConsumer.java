package com.lgk.lgkaicodeservice.mq;

import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.job.ThumbBatchSyncJob;
import com.lgk.lgkaicodeservice.model.dto.thumb.TempThumbDTO;
import com.lgk.lgkaicodeservice.model.mq.ThumbEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 点赞事件消息消费者
 * <p>
 * 监听 RabbitMQ 点赞队列，单条消费后攒入内存队列，由定时任务批量刷写到数据库。
 * <p>
 * 为什么不用 Spring AMQP 的 batchListener 模式？
 * 因为生产者使用 RabbitTemplate 逐条发送，每条消息是独立的 Delivery，
 * 消费端的 batchSize 参数无法将多条独立 Delivery 攒成真正的批量 List。
 * 实际效果：每次 List<ThumbEvent> 只有 1 个元素，批量消费形同虚设。
 * <p>
 * 解决方案：消费端单条接收 → 内存队列攒批 → 定时刷新，实现真正的批量写入。
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
     * 内存攒批队列（无锁，高并发友好）
     * 单条消息到达后立即入队，由定时任务批量取出处理
     */
    private final ConcurrentLinkedQueue<ThumbEvent> buffer = new ConcurrentLinkedQueue<>();

    /**
     * 单条消费点赞事件，攒入内存队列
     * <p>
     * 消息到达后立即 ACK（RabbitTemplate 逐条发送，每条是独立 Delivery），
     * 不依赖 Spring AMQP 的 batchListener 攒批（那个是假的）。
     * 真正的攒批由 flushBuffer() 定时任务完成。
     */
    @RabbitListener(queues = "thumb.event.queue", containerFactory = "thumbSingleContainerFactory")
    public void onMessage(ThumbEvent event) {
        if (event == null) {
            return;
        }
        buffer.offer(event);
        log.debug("点赞事件入队: msgId={}, userId={}, targetId={}, operation={}, 当前队列大小={}",
                event.getMsgId(), event.getUserId(), event.getTargetId(), event.getOperation(), buffer.size());
    }

    /**
     * 定时刷新内存队列，批量写入数据库
     * <p>
     * 触发条件（满足任一即刷新）：
     * - 队列积攒达到 BATCH_SIZE（默认 100 条）
     * - 定时周期到达（默认 1 秒），即使只有 1 条也会处理，避免消息积压
     */
    @Scheduled(fixedRate = ThumbConstant.MQ_FLUSH_INTERVAL_MS)
    public void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        // 一次取出最多 BATCH_SIZE 条
        List<ThumbEvent> batch = new ArrayList<>();
        ThumbEvent event;
        while (batch.size() < ThumbConstant.MQ_BATCH_SIZE && (event = buffer.poll()) != null) {
            batch.add(event);
        }

        if (batch.isEmpty()) {
            return;
        }

        log.info("点赞事件批量刷写，本批 {} 条，剩余 {} 条", batch.size(), buffer.size());
        processBatch(batch);
    }

    /**
     * 应用关闭时，将内存队列中剩余的消息全部刷写到数据库，避免丢失
     */
    @PreDestroy
    public void shutdown() {
        if (buffer.isEmpty()) {
            return;
        }

        log.warn("应用关闭，刷写内存队列中剩余的 {} 条点赞事件", buffer.size());

        List<ThumbEvent> remaining = new ArrayList<>();
        ThumbEvent event;
        while ((event = buffer.poll()) != null) {
            remaining.add(event);
        }

        if (!remaining.isEmpty()) {
            processBatch(remaining);
        }
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
            // 注意：这里不能抛异常，因为消息已经被 ACK 了
            // 失败的消息依赖 ThumbCompensateJob 凌晨补偿
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
