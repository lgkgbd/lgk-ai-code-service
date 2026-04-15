package com.lgk.lgkaicodeservice.job;

import com.lgk.lgkaicodeservice.model.dto.thumb.TempThumbDTO;
import com.lgk.lgkaicodeservice.service.TempThumbStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 点赞补偿任务
 * 每天凌晨执行，确保所有临时点赞记录都已同步到数据库
 */
@Component
@Slf4j
public class ThumbCompensateJob {

    @Resource
    private TempThumbStorageService tempThumbStorageService;

    @Resource
    private ThumbBatchSyncJob thumbBatchSyncJob;

    /**
     * 每天凌晨 2 点执行补偿任务
     * cron 表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void compensate() {
        log.info("开始执行点赞补偿任务...");

        try {
            // 获取所有待同步的记录
            List<TempThumbDTO> allRecords = tempThumbStorageService.getAllPendingSyncRecords();

            if (allRecords.isEmpty()) {
                log.info("点赞补偿任务完成，没有待同步的记录");
                return;
            }

            log.info("点赞补偿任务发现 {} 条待同步记录", allRecords.size());

            // 分批处理
            int batchSize = 100;
            int totalSynced = 0;

            for (int i = 0; i < allRecords.size(); i += batchSize) {
                List<TempThumbDTO> batch = allRecords.subList(i, Math.min(i + batchSize, allRecords.size()));

                // 按操作类型分组
                java.util.Map<Integer, List<TempThumbDTO>> operationGroups = batch.stream()
                        .collect(java.util.stream.Collectors.groupingBy(TempThumbDTO::getOperation));

                // 处理点赞
                List<TempThumbDTO> thumbRecords = operationGroups.getOrDefault(1, java.util.Collections.emptyList());
                if (!thumbRecords.isEmpty()) {
                    thumbBatchSyncJob.processThumbOperations(thumbRecords);
                }

                // 处理取消点赞
                List<TempThumbDTO> unthumbRecords = operationGroups.getOrDefault(0, java.util.Collections.emptyList());
                if (!unthumbRecords.isEmpty()) {
                    thumbBatchSyncJob.processUnthumbOperations(unthumbRecords);
                }

                totalSynced += batch.size();
                log.debug("补偿任务已处理 {}/{} 条记录", totalSynced, allRecords.size());
            }

            // 清除所有已同步的记录
            List<String> allTimeSlices = allRecords.stream()
                    .map(TempThumbDTO::getTimeSlice)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            tempThumbStorageService.clearSyncedRecords(allTimeSlices);

            log.info("点赞补偿任务完成，共同步 {} 条记录", totalSynced);

        } catch (Exception e) {
            log.error("点赞补偿任务执行失败", e);
        }
    }

}
