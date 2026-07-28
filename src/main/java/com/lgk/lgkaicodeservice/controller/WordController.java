package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.DeleteRequest;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.model.dto.word.WordCaptureRequest;
import com.lgk.lgkaicodeservice.model.dto.word.WordNoteUpdateRequest;
import com.lgk.lgkaicodeservice.model.dto.word.WordQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.entity.WordDict;
import com.lgk.lgkaicodeservice.model.vo.WordCardVO;
import com.lgk.lgkaicodeservice.model.vo.WordStatisticsVO;
import com.lgk.lgkaicodeservice.service.WordDictService;
import com.lgk.lgkaicodeservice.service.WordService;
import com.lgk.lgkaicodeservice.service.word.Bookmarklet;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.lgk.lgkaicodeservice.service.UserService;

import java.util.List;

/**
 * 单词记忆 控制层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/word")
public class WordController {

    @Resource
    private WordService wordService;

    @Resource
    private WordDictService wordDictService;

    @Resource
    private UserService userService;

    /**
     * 录入（单词/多词/整句统一入口）
     */
    @PostMapping("/capture")
    public BaseResponse<List<WordCardVO>> capture(@RequestBody WordCaptureRequest wordCaptureRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(wordCaptureRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<WordCardVO> cards = wordService.capture(wordCaptureRequest, loginUser);
        return ResultUtils.success(cards);
    }

    /**
     * 我的词库分页
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<WordCardVO>> listMyWordsByPage(@RequestBody WordQueryRequest wordQueryRequest,
                                                            HttpServletRequest request) {
        ThrowUtils.throwIf(wordQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wordService.listMyWords(wordQueryRequest, loginUser));
    }

    /**
     * 词卡详情（含全部遇见记录）
     */
    @GetMapping("/get/vo")
    public BaseResponse<WordCardVO> getWordCardVO(long userWordId, HttpServletRequest request) {
        ThrowUtils.throwIf(userWordId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wordService.getWordCardVO(userWordId, loginUser));
    }

    /**
     * 更新私人笔记
     */
    @PostMapping("/note/update")
    public BaseResponse<Boolean> updateNote(@RequestBody WordNoteUpdateRequest wordNoteUpdateRequest,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(wordNoteUpdateRequest == null || wordNoteUpdateRequest.getUserWordId() == null
                || wordNoteUpdateRequest.getUserWordId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean result = wordService.updateNote(
                wordNoteUpdateRequest.getUserWordId(), wordNoteUpdateRequest.getNote(), loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 删除词卡
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteWord(@RequestBody DeleteRequest deleteRequest,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null
                || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wordService.deleteWord(deleteRequest.getId(), loginUser));
    }

    /**
     * 统计：总词数 / 今日新增 / 待复习 / 已掌握 / 连续打卡
     */
    @GetMapping("/statistics")
    public BaseResponse<WordStatisticsVO> statistics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(wordService.statistics(loginUser));
    }

    /**
     * 查词联想（录入前预览）
     */
    @GetMapping("/dict/search")
    public BaseResponse<List<WordDict>> dictSearch(String keyword, HttpServletRequest request) {
        // 需登录，避免被当公共词典接口刷
        userService.getLoginUser(request);
        List<WordDict> list = wordDictService.search(keyword, WordConstant.DEFAULT_LANG, 10);
        return ResultUtils.success(list);
    }

    /**
     * 书签小工具脚本。返回一段 javascript:，用户拖到书签栏即可在任意网页选中单词一键录入。
     * <p>
     * 直接返回可执行 JS 文本（text/javascript），前端把它包成 <a href="javascript:..."> 让用户拖拽。
     */
    @GetMapping(value = "/bookmarklet/script", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String bookmarkletScript(HttpServletRequest request) {
        // 用当前请求推导后端 base，脚本里 POST 回 /api/word/capture
        String base = deriveApiBase(request);
        return Bookmarklet.script(base);
    }

    private String deriveApiBase(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath(); // /api
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (!(port == 80 && "http".equals(scheme)) && !(port == 443 && "https".equals(scheme))) {
            sb.append(':').append(port);
        }
        sb.append(contextPath);
        return sb.toString();
    }
}
