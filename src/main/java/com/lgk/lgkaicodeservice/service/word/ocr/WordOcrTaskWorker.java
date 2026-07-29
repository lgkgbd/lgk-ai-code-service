package com.lgk.lgkaicodeservice.service.word.ocr;

import cn.hutool.core.io.FileUtil;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.manager.StorageManager;
import com.lgk.lgkaicodeservice.model.enums.FileUploadBizEnum;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import com.lgk.lgkaicodeservice.utils.ImageCompressor;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别任务的后台执行体
 * <p>
 * 单独成 Bean 而非放在 Service 里，是因为 {@code @Async} 依赖 Spring 代理，
 * 同类内部自调用会绕过代理导致异步失效——这是最经典的踩坑点。
 * <p>
 * 执行顺序（压缩放在最前）：
 * <ol>
 *   <li>压缩：手机直出 4MB 照片先压到 ~400KB，既过得了 MinIO 的 1MB 限，又省 VL 的钱和时间</li>
 *   <li>存 MinIO：留原图做溯源，失败不影响识别（存图只是锦上添花）</li>
 *   <li>调 VL 识别，每完成一张推进一次进度</li>
 *   <li>跨图合并去重，写回任务</li>
 * </ol>
 */
@Slf4j
@Component
public class WordOcrTaskWorker {

    @Resource
    private WordOcrRecognizer recognizer;

    @Resource
    private WordOcrTaskManager taskManager;

    @Resource
    private StorageManager storageManager;

    /**
     * 短链访问前缀，与 ShortLinkController 的 {@code @RequestMapping("/s")} 对应
     */
    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 待识别的一张图（已从 MultipartFile 读入内存）
     * <p>
     * 必须在请求线程里读完字节：MultipartFile 背后的临时文件会随请求结束被清理，
     * 异步线程再去读就是空的。
     */
    @Data
    @AllArgsConstructor
    public static class RawImage {

        private byte[] bytes;

        private String contentType;

        private String filename;
    }

    /**
     * 异步执行识别任务。整个方法自吞异常——任何失败都转成任务的 FAILED 状态，
     * 绝不能让异常逃逸到线程池里变成一条没人看的日志
     *
     * @param taskId 任务 id
     * @param userId 归属用户
     * @param images 待识别图片
     */
    @Async("taskExecutor")
    public void run(String taskId, long userId, List<RawImage> images) {
        log.info("拍照识别任务启动，taskId={}, userId={}, 图片数={}", taskId, userId, images.size());
        try {
            // 1. 压缩 + 存图
            List<RawImage> compressed = new ArrayList<>(images.size());
            List<String> imageUrls = new ArrayList<>(images.size());
            for (RawImage image : images) {
                ImageCompressor.Result result = ImageCompressor.compress(
                        image.getBytes(), image.getContentType(),
                        WordConstant.OCR_IMAGE_MAX_EDGE, WordConstant.OCR_IMAGE_JPEG_QUALITY);
                RawImage ready = new RawImage(result.getBytes(), result.getContentType(),
                        result.isCompressed() ? replaceSuffixWithJpg(image.getFilename()) : image.getFilename());
                compressed.add(ready);
                String url = storeQuietly(userId, ready);
                if (url != null) {
                    imageUrls.add(url);
                }
            }
            taskManager.markRunning(taskId, imageUrls);

            // 2. 逐张识别。单张失败不拖垮整批——3 张里坏 1 张，另外 2 张的结果照样有用
            List<WordOcrItemVO> all = new ArrayList<>();
            int done = 0;
            int failed = 0;
            String lastError = null;
            for (RawImage image : compressed) {
                try {
                    all.addAll(recognizer.recognize(image.getBytes(), image.getContentType()));
                } catch (Exception e) {
                    failed++;
                    lastError = e.getMessage();
                    log.error("单张图片识别失败，跳过，taskId={}, file={}", taskId, image.getFilename(), e);
                }
                taskManager.advance(taskId, ++done);
            }

            // 3. 全军覆没才算任务失败
            if (failed == compressed.size()) {
                taskManager.markFailed(taskId, "图片识别失败：" + (lastError == null ? "未知原因" : lastError));
                log.warn("拍照识别任务全部失败，taskId={}", taskId);
                return;
            }

            // 4. 跨图合并去重（normalize 幂等，直接复用）
            List<WordOcrItemVO> merged = WordOcrRecognizer.normalize(all);
            taskManager.markSucceed(taskId, merged);
            log.info("拍照识别任务完成，taskId={}, 候选词={} 个（{} 张图失败）", taskId, merged.size(), failed);
        } catch (Exception e) {
            log.error("拍照识别任务异常，taskId={}", taskId, e);
            taskManager.markFailed(taskId, "识别失败：" + e.getMessage());
        }
    }

    /**
     * 存原图到 MinIO 并返回短链。存图只是为了日后溯源，失败不该影响识别主流程，
     * 所以这里吞掉异常返回 null
     */
    private String storeQuietly(long userId, RawImage image) {
        try {
            String uuid = RandomStringUtils.randomAlphanumeric(8);
            String filename = uuid + "-" + image.getFilename();
            String key = String.format("%s/%s/%s", FileUploadBizEnum.WORD_OCR.getValue(), userId, filename);
            String shortCode = storageManager.uploadBytes(key, image.getBytes(), image.getContentType());
            return String.format("%s/s/%s", contextPath, shortCode);
        } catch (Exception e) {
            log.warn("识别原图存储失败，不影响识别，userId={}, file={}", userId, image.getFilename(), e);
            return null;
        }
    }

    /**
     * 压缩后一律是 JPEG，文件名后缀跟着改，免得 MinIO 里存着 .png 实际是 jpeg
     */
    private static String replaceSuffixWithJpg(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        String base = FileUtil.mainName(filename);
        return (base == null || base.isBlank() ? "photo" : base) + ".jpg";
    }
}
