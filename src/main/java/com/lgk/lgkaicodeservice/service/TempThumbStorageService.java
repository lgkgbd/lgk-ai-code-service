package com.lgk.lgkaicodeservice.service;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.model.dto.thumb.TempThumbDTO;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 临时点赞存储服务
 * 负责将点赞操作临时存储到 Redis，后续批量同步到数据库
 */
@Service
@Slf4j
public class TempThumbStorageService {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 保存临时点赞记录
     *
     * @param userId    用户ID
     * @param type      点赞类型
     * @param targetId  目标ID
     * @param thumbId   点赞记录ID（取消点赞时为 null）
     * @param operation 操作类型：1-点赞，0-取消点赞
     */
    public void saveTempThumb(Long userId, ThumbTypeEnum type, Long targetId, Long thumbId, int operation) {
        try {
            String timeSlice = getCurrentTimeSlice();
            String tempKey = ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(timeSlice);

            TempThumbDTO tempThumb = new TempThumbDTO();
            tempThumb.setThumbId(thumbId);
            tempThumb.setUserId(userId);
            tempThumb.setType(type.getCode());
            tempThumb.setTargetId(targetId);
            tempThumb.setOperation(operation);
            tempThumb.setCreateTime(LocalDateTime.now());
            tempThumb.setTimeSlice(timeSlice);

            // 添加到时间片对应的列表
            RList<TempThumbDTO> tempList = redissonClient.getList(tempKey);
            tempList.add(tempThumb);

            // 将 key 加入待同步队列（用于后续批量处理）
            RSet<String> queue = redissonClient.getSet(ThumbConstant.TEMP_THUMB_QUEUE_KEY);
            queue.add(tempKey);

            log.debug("临时点赞记录已保存: userId={}, targetId={}, operation={}, timeSlice={}",
                    userId, targetId, operation, timeSlice);
        } catch (Exception e) {
            log.error("保存临时点赞记录失败: userId={}, targetId={}", userId, targetId, e);
            throw new RuntimeException("保存临时点赞记录失败", e);
        }
    }

    /**
     * 获取当前时间片（每10秒一个时间片）
     * 格式: yyyyMMddHHmmss 的前 15 位（精确到 10 秒）
     */
    public String getCurrentTimeSlice() {
        DateTime now = DateUtil.date();
        String timestamp = DateUtil.format(now, "yyyyMMddHHmmss");
        // 取前 15 位，秒数取整到 10 秒
        int second = Integer.parseInt(timestamp.substring(12, 14));
        int slice = (second / 10) * 10;
        return timestamp.substring(0, 12) + String.format("%02d", slice);
    }

    /**
     * 获取待同步的临时点赞记录
     *
     * @param batchSize 每批次数量
     * @return 临时点赞记录列表
     */
    public List<TempThumbDTO> getPendingSyncRecords(int batchSize) {
        List<TempThumbDTO> result = new ArrayList<>();
        try {
            RSet<String> queue = redissonClient.getSet(ThumbConstant.TEMP_THUMB_QUEUE_KEY);
            Set<String> keys = queue.readAll();

            if (keys == null || keys.isEmpty()) {
                return result;
            }

            // 按时间片排序，优先处理较早的数据
            List<String> sortedKeys = keys.stream()
                    .sorted()
                    .collect(Collectors.toList());

            for (String key : sortedKeys) {
                if (result.size() >= batchSize) {
                    break;
                }

                RList<TempThumbDTO> tempList = redissonClient.getList(key);
                List<TempThumbDTO> records = tempList.readAll();

                if (records != null && !records.isEmpty()) {
                    result.addAll(records);
                }

                // 如果该时间片的数据已全部取出，从队列中移除
                if (result.size() >= batchSize || records.size() <= batchSize - result.size()) {
                    queue.remove(key);
                }
            }

            // 限制返回数量
            if (result.size() > batchSize) {
                return result.subList(0, batchSize);
            }

            return result;
        } catch (Exception e) {
            log.error("获取待同步临时点赞记录失败", e);
            return result;
        }
    }

    /**
     * 获取所有待同步的临时点赞记录（用于补偿任务）
     *
     * @return 所有临时点赞记录
     */
    public List<TempThumbDTO> getAllPendingSyncRecords() {
        List<TempThumbDTO> result = new ArrayList<>();
        try {
            RSet<String> queue = redissonClient.getSet(ThumbConstant.TEMP_THUMB_QUEUE_KEY);
            Set<String> keys = queue.readAll();

            if (keys == null || keys.isEmpty()) {
                return result;
            }

            for (String key : keys) {
                RList<TempThumbDTO> tempList = redissonClient.getList(key);
                List<TempThumbDTO> records = tempList.readAll();
                if (records != null && !records.isEmpty()) {
                    result.addAll(records);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("获取所有待同步临时点赞记录失败", e);
            return result;
        }
    }

    /**
     * 清除已同步的临时点赞记录
     *
     * @param timeSlices 已同步的时间片列表
     */
    public void clearSyncedRecords(List<String> timeSlices) {
        if (timeSlices == null || timeSlices.isEmpty()) {
            return;
        }

        try {
            for (String timeSlice : timeSlices) {
                String tempKey = ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(timeSlice);
                redissonClient.getList(tempKey).delete();
                log.debug("已清除同步完成的临时点赞记录: timeSlice={}", timeSlice);
            }
        } catch (Exception e) {
            log.error("清除已同步临时点赞记录失败", e);
        }
    }

    /**
     * 获取待同步记录数量
     *
     * @return 待同步记录数
     */
    public int getPendingCount() {
        try {
            RSet<String> queue = redissonClient.getSet(ThumbConstant.TEMP_THUMB_QUEUE_KEY);
            Set<String> keys = queue.readAll();

            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (String key : keys) {
                RList<TempThumbDTO> tempList = redissonClient.getList(key);
                count += tempList.size();
            }
            return count;
        } catch (Exception e) {
            log.error("获取待同步记录数量失败", e);
            return 0;
        }
    }

}
