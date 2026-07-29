package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.WordOcrTaskVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 拍照录入：便利贴照片 → 候选生词
 * <p>
 * 走「提交即返回 + 后台异步识别 + 前端轮询」的任务模式：VL 识别单张要 2~5 秒，
 * 多张叠加更久，同步等待会把用户卡死在转圈上。
 * <p>
 * 注意识别结果<b>不会自动入库</b>：手写识别不可能全对，必须让用户在前端确认、
 * 改错后再调 {@link WordService#capture} 真正录入。
 */
public interface WordOcrService {

    /**
     * 提交识别任务（立即返回，不阻塞）
     *
     * @param files     图片，1~{@link com.lgk.lgkaicodeservice.constant.WordConstant#MAX_OCR_IMAGES} 张
     * @param loginUser 当前登录用户
     * @return 任务 id，前端拿它轮询 {@link #getTask}
     */
    String submit(MultipartFile[] files, User loginUser);

    /**
     * 查询任务进度 / 结果
     *
     * @param taskId    任务 id
     * @param loginUser 当前登录用户（校验归属，防止拿别人 taskId 偷看）
     * @return 任务快照
     */
    WordOcrTaskVO getTask(String taskId, User loginUser);
}
