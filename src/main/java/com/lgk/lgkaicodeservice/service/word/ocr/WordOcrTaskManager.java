package com.lgk.lgkaicodeservice.service.word.ocr;

import com.google.gson.Gson;
import com.lgk.lgkaicodeservice.constant.RedisConstant;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.model.enums.WordOcrStatusEnum;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import com.lgk.lgkaicodeservice.model.vo.WordOcrTaskVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 拍照识别任务的状态存取
 * <p>
 * 任务态是纯临时数据：用户确认入库后这份识别结果就没有任何价值了，
 * 因此只放 Redis（TTL 1h）而不建表，省掉一整套 CRUD 与清理逻辑。
 * <p>
 * 存储结构：{@code word:ocr:task:{taskId}} → {@link WordOcrTaskVO} 的 JSON 串。
 * 用 StringCodec 显式指定编码，避免依赖 Redisson 默认 codec（换 codec 时老数据不会读崩）。
 */
@Slf4j
@Component
public class WordOcrTaskManager {

    private static final Gson GSON = new Gson();

    @Resource
    private RedissonClient redissonClient;

    private RBucket<String> bucket(String taskId) {
        return redissonClient.getBucket(RedisConstant.getWordOcrTaskKey(taskId), StringCodec.INSTANCE);
    }

    /**
     * 创建任务，初始状态 PENDING
     *
     * @param taskId      任务 id
     * @param userId      归属用户
     * @param totalImages 图片总数
     * @return 新建的任务
     */
    public WordOcrTaskVO create(String taskId, long userId, int totalImages) {
        WordOcrTaskVO task = WordOcrTaskVO.builder()
                .taskId(taskId)
                .userId(userId)
                .status(WordOcrStatusEnum.PENDING.getValue())
                .totalImages(totalImages)
                .doneImages(0)
                .imageUrls(List.of())
                .items(List.of())
                .createTime(System.currentTimeMillis())
                .build();
        save(task);
        return task;
    }

    /**
     * 读取任务
     *
     * @param taskId 任务 id
     * @return 任务；不存在或已过期返回 null
     */
    public WordOcrTaskVO get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        String json = bucket(taskId).get();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(json, WordOcrTaskVO.class);
        } catch (Exception e) {
            log.warn("识别任务反序列化失败，taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 写回任务并续期 TTL
     */
    public void save(WordOcrTaskVO task) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        bucket(task.getTaskId()).set(GSON.toJson(task),
                Duration.ofSeconds(WordConstant.OCR_TASK_TTL_SECONDS));
    }

    /**
     * 标记为识别中
     */
    public void markRunning(String taskId, List<String> imageUrls) {
        WordOcrTaskVO task = get(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(WordOcrStatusEnum.RUNNING.getValue());
        if (imageUrls != null) {
            task.setImageUrls(imageUrls);
        }
        save(task);
    }

    /**
     * 推进进度（每识别完一张图调一次），让前端能显示「2/3 已识别」
     */
    public void advance(String taskId, int doneImages) {
        WordOcrTaskVO task = get(taskId);
        if (task == null) {
            return;
        }
        task.setDoneImages(doneImages);
        save(task);
    }

    /**
     * 标记为识别成功
     */
    public void markSucceed(String taskId, List<WordOcrItemVO> items) {
        WordOcrTaskVO task = get(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(WordOcrStatusEnum.SUCCEED.getValue());
        task.setItems(items == null ? List.of() : items);
        task.setDoneImages(task.getTotalImages());
        save(task);
    }

    /**
     * 标记为识别失败
     */
    public void markFailed(String taskId, String errorMsg) {
        WordOcrTaskVO task = get(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(WordOcrStatusEnum.FAILED.getValue());
        task.setErrorMsg(errorMsg);
        save(task);
    }

    /**
     * 删除任务（确认入库后可主动清理，不等 TTL）
     */
    public void remove(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        bucket(taskId).delete();
    }
}
