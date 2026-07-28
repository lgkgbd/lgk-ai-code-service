package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.dto.word.WordCaptureRequest;
import com.lgk.lgkaicodeservice.model.dto.word.WordQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.entity.UserWord;
import com.lgk.lgkaicodeservice.model.vo.WordCardVO;
import com.lgk.lgkaicodeservice.model.vo.WordStatisticsVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 单词学习服务：录入主链路 + 我的词库
 */
public interface WordService extends IService<UserWord> {

    /**
     * 录入（单词 / 多词 / 整句统一入口）—— 本功能最核心的方法
     * <p>
     * 流程见 docs/单词记忆功能-开发计划.md 4.4 节。幂等 + 并发安全（依赖 uq_user_dict 唯一索引，
     * 捕获 DuplicateKeyException 后转 encounterNum+1），返回值区分每个词「新增」还是「第 N 次遇见」。
     *
     * @param request   录入请求
     * @param loginUser 当前登录用户
     * @return 每个候选词一张卡，含 newlyAdded / encounterNum
     */
    List<WordCardVO> capture(WordCaptureRequest request, User loginUser);

    /**
     * 我的词库分页（按掌握度 / 关键词筛选）
     */
    Page<WordCardVO> listMyWords(WordQueryRequest request, User loginUser);

    /**
     * 词卡详情（含全部遇见记录）
     */
    WordCardVO getWordCardVO(long userWordId, User loginUser);

    /**
     * 更新私人笔记
     */
    boolean updateNote(long userWordId, String note, User loginUser);

    /**
     * 删除词卡（逻辑删除并移出复习队列）
     */
    boolean deleteWord(long userWordId, User loginUser);

    /**
     * 统计：总词数 / 今日新增 / 待复习 / 已掌握 / 连续打卡
     */
    WordStatisticsVO statistics(User loginUser);
}
