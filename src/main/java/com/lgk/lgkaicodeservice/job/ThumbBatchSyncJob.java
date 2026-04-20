package com.lgk.lgkaicodeservice.job;

import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.model.dto.thumb.TempThumbDTO;
import com.lgk.lgkaicodeservice.model.entity.Thumb;
import com.lgk.lgkaicodeservice.service.TempThumbStorageService;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandler;
import com.lgk.lgkaicodeservice.service.thumb.ThumbHandlerFactory;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgk.lgkaicodeservice.mapper.ThumbMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 点赞批量同步任务
 * 每10秒执行一次，将 Redis 中的临时点赞记录批量同步到数据库
 */
@Component
@Slf4j
public class ThumbBatchSyncJob extends ServiceImpl<ThumbMapper, Thumb> {

    @Resource
    private TempThumbStorageService tempThumbStorageService;

    @Resource
    private ThumbHandlerFactory thumbHandlerFactory;

    /**
     * 每10秒执行一次批量同步
     * <p>
     * 注意：此 Job 只处理降级路径（MQ 发送失败时写入 TempThumbStorageService 的数据）。
     * MQ 主路径的消息由 ThumbEventConsumer 直接消费，不经过此 Job。
     */
    @Scheduled(fixedRate = ThumbConstant.BATCH_SYNC_INTERVAL_SECONDS * 1000)
    public void batchSync() {
        int pendingCount = tempThumbStorageService.getPendingCount();
        if (pendingCount == 0) {
            return;
        }

        log.info("开始执行点赞批量同步（降级路径），待同步记录数: {}", pendingCount);

        try {
            // 获取待同步记录
            List<TempThumbDTO> records = tempThumbStorageService.getPendingSyncRecords(ThumbConstant.BATCH_SYNC_SIZE);

            if (records.isEmpty()) {
                return;
            }

            // 按 userId:targetId:type 对同一批次的操作进行合并，保留最终状态
            // 同一用户对同一目标先点赞再取消，最终操作为 "抵消"，不需要同步数据库
            Map<String, TempThumbDTO> mergedMap = new LinkedHashMap<>();
            for (TempThumbDTO record : records) {
                String key = record.getUserId() + ":" + record.getTargetId() + ":" + record.getType();
                TempThumbDTO existing = mergedMap.get(key);
                if (existing == null) {
                    mergedMap.put(key, record);
                } else {
                    // 如果两次操作不同（一次点赞一次取消），互相抵消，从 map 中移除
                    if (!existing.getOperation().equals(record.getOperation())) {
                        mergedMap.remove(key);
                        log.debug("点赞操作互相抵消，跳过同步: userId={}, targetId={}", record.getUserId(), record.getTargetId());
                    } else {
                        // 相同操作则保留最新一条（幂等）
                        mergedMap.put(key, record);
                    }
                }
            }

            List<TempThumbDTO> mergedRecords = new ArrayList<>(mergedMap.values());

            // 按操作类型分组处理
            Map<Integer, List<TempThumbDTO>> operationGroups = mergedRecords.stream()
                    .collect(Collectors.groupingBy(TempThumbDTO::getOperation));

            // 处理点赞操作
            List<TempThumbDTO> thumbRecords = operationGroups.getOrDefault(1, Collections.emptyList());
            if (!thumbRecords.isEmpty()) {
                processThumbOperations(thumbRecords);
            }

            // 处理取消点赞操作
            List<TempThumbDTO> unthumbRecords = operationGroups.getOrDefault(0, Collections.emptyList());
            if (!unthumbRecords.isEmpty()) {
                processUnthumbOperations(unthumbRecords);
            }

            // 清除已同步的记录
            List<String> syncedTimeSlices = records.stream()
                    .map(TempThumbDTO::getTimeSlice)
                    .distinct()
                    .collect(Collectors.toList());
            tempThumbStorageService.clearSyncedRecords(syncedTimeSlices);

            log.info("点赞批量同步完成，本次同步 {} 条记录（点赞: {}, 取消: {}, 抵消跳过: {}）",
                    records.size(), thumbRecords.size(), unthumbRecords.size(),
                    records.size() - mergedRecords.size());

        } catch (Exception e) {
            log.error("点赞批量同步失败", e);
        }
    }

    /**
     * 处理点赞操作（批量检查 + 批量插入）
     * 传入的 records 已经经过同批次合并去重，不会出现同一用户同一目标同时点赞又取消的情况
     */
    @Transactional(rollbackFor = Exception.class)
    public void processThumbOperations(List<TempThumbDTO> records) {
        // 1. 按 userId + targetId + type 去重，保留最新的操作（幂等保护）
        Map<String, TempThumbDTO> uniqueRecords = new LinkedHashMap<>();
        for (TempThumbDTO record : records) {
            String key = record.getUserId() + ":" + record.getTargetId() + ":" + record.getType();
            uniqueRecords.put(key, record);
        }
        List<TempThumbDTO> dedupedRecords = new ArrayList<>(uniqueRecords.values());

        // 2. 按类型分组
        Map<Integer, List<TempThumbDTO>> typeGroups = dedupedRecords.stream()
                .collect(Collectors.groupingBy(TempThumbDTO::getType));

        for (Map.Entry<Integer, List<TempThumbDTO>> entry : typeGroups.entrySet()) {
            Integer type = entry.getKey();
            List<TempThumbDTO> typeRecords = entry.getValue();

            try {
                // 3. 批量查询已存在的记录
                Set<String> existingKeys = batchCheckExists(type, typeRecords);

                // 4. 过滤出需要插入的记录（数据库中不存在的才插入）
                List<TempThumbDTO> toInsert = typeRecords.stream()
                        .filter(dto -> {
                            String key = dto.getUserId() + ":" + dto.getTargetId();
                            return !existingKeys.contains(key);
                        })
                        .collect(Collectors.toList());

                // 5. 批量插入
                if (!toInsert.isEmpty()) {
                    List<Thumb> thumbs = toInsert.stream()
                            .map(dto -> {
                                Thumb thumb = new Thumb();
                                thumb.setId(dto.getThumbId());
                                thumb.setUserId(dto.getUserId());
                                thumb.setType(type);
                                thumb.setTargetId(dto.getTargetId());
                                thumb.setCreateTime(dto.getCreateTime());
                                return thumb;
                            })
                            .collect(Collectors.toList());

                    this.saveBatch(thumbs);
                    log.debug("批量插入 {} 条点赞记录", thumbs.size());
                }

                // 6. 仅对实际插入成功的记录更新数据库点赞数（聚合 UPDATE）
                // 注意：Redis 计数在实时操作时已更新，这里只同步数据库
                ThumbHandler handler = thumbHandlerFactory.getHandlerByCode(type);
                Map<Long, Long> targetCountMap = toInsert.stream()
                        .collect(Collectors.groupingBy(TempThumbDTO::getTargetId, Collectors.counting()));

                try {
                    // 聚合写：N 条点赞 → 1 次 UPDATE thumbNum = thumbNum + N
                    handler.incrementThumbBatch(targetCountMap);
                    log.debug("批量更新点赞数，targetCountMap={}", targetCountMap);
                } catch (Exception e) {
                    log.error("批量更新点赞数失败: targetCountMap={}", targetCountMap, e);
                }

            } catch (Exception e) {
                log.error("批量处理点赞操作失败, type={}", type, e);
            }
        }
    }

    /**
     * 批量检查点赞记录是否已存在
     */
    private Set<String> batchCheckExists(Integer type, List<TempThumbDTO> records) {
        if (records.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> existingKeys = new HashSet<>();

        // 提取所有 userId 和 targetId
        Set<Long> userIds = records.stream()
                .map(TempThumbDTO::getUserId)
                .collect(Collectors.toSet());
        Set<Long> targetIds = records.stream()
                .map(TempThumbDTO::getTargetId)
                .collect(Collectors.toSet());

        // 批量查询
        QueryWrapper wrapper = new QueryWrapper()
                .eq("type", type)
                .in("userId", userIds)
                .in("targetId", targetIds);

        List<Thumb> existingThumbs = this.list(wrapper);

        // 构建已存在的 key 集合
        for (Thumb thumb : existingThumbs) {
            existingKeys.add(thumb.getUserId() + ":" + thumb.getTargetId());
        }

        return existingKeys;
    }

    /**
     * 处理取消点赞操作
     * 只有数据库中真实存在记录并删除成功，才更新点赞数
     */
    @Transactional(rollbackFor = Exception.class)
    public void processUnthumbOperations(List<TempThumbDTO> records) {
        // 按 userId + targetId + type 去重（幂等保护）
        Map<String, TempThumbDTO> uniqueRecords = new LinkedHashMap<>();
        for (TempThumbDTO record : records) {
            String key = record.getUserId() + ":" + record.getTargetId() + ":" + record.getType();
            uniqueRecords.put(key, record);
        }
        List<TempThumbDTO> dedupedRecords = new ArrayList<>(uniqueRecords.values());

        // 按类型分组
        Map<Integer, List<TempThumbDTO>> typeGroups = dedupedRecords.stream()
                .collect(Collectors.groupingBy(TempThumbDTO::getType));

        for (Map.Entry<Integer, List<TempThumbDTO>> entry : typeGroups.entrySet()) {
            Integer type = entry.getKey();
            List<TempThumbDTO> typeRecords = entry.getValue();

            try {
                // 先批量查询确实存在的点赞记录
                Set<String> existingKeys = batchCheckExists(type, typeRecords);

                // 只处理数据库中真实存在的取消点赞
                List<TempThumbDTO> toDelete = typeRecords.stream()
                        .filter(dto -> existingKeys.contains(dto.getUserId() + ":" + dto.getTargetId()))
                        .collect(Collectors.toList());

                if (toDelete.isEmpty()) {
                    log.debug("取消点赞：数据库中无对应记录，跳过 thumbNum 更新, type={}", type);
                    continue;
                }

                // 批量删除点赞记录
                for (TempThumbDTO dto : toDelete) {
                    QueryWrapper wrapper = new QueryWrapper()
                            .eq("userId", dto.getUserId())
                            .eq("type", type)
                            .eq("targetId", dto.getTargetId());
                    this.remove(wrapper);
                }

                // 聚合写：多条取消点赞 → 1 次 UPDATE thumbNum = thumbNum - N
                ThumbHandler handler = thumbHandlerFactory.getHandlerByCode(type);
                List<Long> targetIds = toDelete.stream()
                        .map(TempThumbDTO::getTargetId)
                        .collect(Collectors.toList());
                try {
                    handler.decrementThumbBatch(targetIds);
                    log.debug("批量更新取消点赞数，targetIds={}", targetIds);
                } catch (Exception e) {
                    log.error("批量更新取消点赞数失败: targetIds={}", targetIds, e);
                }

                log.debug("取消点赞：成功删除 {} 条记录（跳过 {} 条不存在的）",
                        toDelete.size(), typeRecords.size() - toDelete.size());

            } catch (Exception e) {
                log.error("批量处理取消点赞操作失败, type={}", type, e);
            }
        }
    }

}
