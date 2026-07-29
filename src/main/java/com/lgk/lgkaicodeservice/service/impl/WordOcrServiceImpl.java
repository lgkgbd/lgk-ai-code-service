package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.io.FileUtil;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.WordOcrTaskVO;
import com.lgk.lgkaicodeservice.service.WordOcrService;
import com.lgk.lgkaicodeservice.service.word.ocr.WordOcrTaskManager;
import com.lgk.lgkaicodeservice.service.word.ocr.WordOcrTaskWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 拍照录入服务实现
 * <p>
 * 本类只做「同步的那一半」：校验 + 读字节 + 建任务，然后立刻返回 taskId。
 * 耗时的压缩 / 存图 / VL 调用全交给 {@link WordOcrTaskWorker} 在线程池里跑。
 */
@Slf4j
@Service
public class WordOcrServiceImpl implements WordOcrService {

    @Resource
    private WordOcrTaskManager taskManager;

    @Resource
    private WordOcrTaskWorker taskWorker;

    @Override
    public String submit(MultipartFile[] files, User loginUser) {
        ThrowUtils.throwIf(files == null || files.length == 0, ErrorCode.PARAMS_ERROR, "请至少上传一张图片");
        ThrowUtils.throwIf(files.length > WordConstant.MAX_OCR_IMAGES, ErrorCode.PARAMS_ERROR,
                "单次最多上传 " + WordConstant.MAX_OCR_IMAGES + " 张图片");
        long userId = loginUser.getId();

        // 必须在请求线程里就把字节读出来：MultipartFile 背后的临时文件随请求结束即被清理，
        // 异步线程再去读只会拿到空内容
        List<WordOcrTaskWorker.RawImage> images = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            validate(file);
            try {
                images.add(new WordOcrTaskWorker.RawImage(
                        file.getBytes(),
                        file.getContentType(),
                        file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename()));
            } catch (IOException e) {
                log.error("读取上传图片失败，userId={}, file={}", userId, file.getOriginalFilename(), e);
                ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "图片读取失败");
            }
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        taskManager.create(taskId, userId, images.size());
        // 提交即返回，用户不用干等 VL
        taskWorker.run(taskId, userId, images);
        log.info("拍照识别任务已提交，taskId={}, userId={}, 图片数={}", taskId, userId, images.size());
        return taskId;
    }

    @Override
    public WordOcrTaskVO getTask(String taskId, User loginUser) {
        ThrowUtils.throwIf(taskId == null || taskId.isBlank(), ErrorCode.PARAMS_ERROR);
        WordOcrTaskVO task = taskManager.get(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "识别任务不存在或已过期");
        // 别人的 taskId 一律当不存在，不泄露任务是否存在
        ThrowUtils.throwIf(!Objects.equals(task.getUserId(), loginUser.getId()),
                ErrorCode.NOT_FOUND_ERROR, "识别任务不存在或已过期");
        return task;
    }

    /**
     * 单张图片校验：非空 + 大小 + 后缀
     */
    private void validate(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "图片内容为空");
        ThrowUtils.throwIf(file.getSize() > WordConstant.MAX_OCR_UPLOAD_BYTES, ErrorCode.PARAMS_ERROR,
                String.format("单张图片不能超过 %dMB（当前 %.1fMB）",
                        WordConstant.MAX_OCR_UPLOAD_BYTES / 1024 / 1024,
                        file.getSize() / 1024.0 / 1024.0));
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        suffix = suffix == null ? "" : suffix.toLowerCase();
        ThrowUtils.throwIf(!WordConstant.OCR_ALLOWED_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR,
                "仅支持图片格式：" + String.join("/", WordConstant.OCR_ALLOWED_SUFFIX));
    }
}
