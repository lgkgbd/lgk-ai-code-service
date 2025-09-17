package com.lgk.lgkaicodeservice.controller;

import com.lgk.lgkaicodeservice.common.BaseResponse;
import com.lgk.lgkaicodeservice.common.ResultUtils;
import com.lgk.lgkaicodeservice.datasync.CanalClient;
import com.lgk.lgkaicodeservice.datasync.PostDataSyncService;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 数据同步控制器
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@RestController
@RequestMapping("/datasync")
@Slf4j
@Tag(name = "数据同步管理", description = "Canal数据同步相关接口")
public class DataSyncController {

    @Resource
    private CanalClient canalClient;

    @Resource
    private PostDataSyncService postDataSyncService;

    @PostMapping("/canal/restart")
    @Operation(summary = "重启Canal客户端")
    public BaseResponse<String> restartCanal() {
        try {
            canalClient.restart();
            return ResultUtils.success("Canal客户端重启成功");
        } catch (Exception e) {
            log.error("重启Canal客户端失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重启Canal客户端失败"+ e.getMessage());
        }
    }

    @PostMapping("/post/sync-all")
    @Operation(summary = "全量同步Post数据到ES")
    public BaseResponse<String> syncAllPosts() {
        try {
            long count = postDataSyncService.syncAllPostsToEs();
            return ResultUtils.success("全量同步完成，共同步 " + count + " 条数据");
        } catch (Exception e) {
            log.error("全量同步Post数据失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "全量同步失败"+ e.getMessage());
        }
    }

    @PostMapping("/post/sync/{id}")
    @Operation(summary = "同步指定Post到ES")
    public BaseResponse<String> syncPostById(@PathVariable Long id) {
        try {
            boolean success = postDataSyncService.syncPostToEs(id);
            if (success) {
                return ResultUtils.success("Post同步成功");
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Post不存在或同步失败");
            }
        } catch (Exception e) {
            log.error("同步Post数据失败, ID: {}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步失败"+ e.getMessage());
        }
    }

    @DeleteMapping("/post/es/{id}")
    @Operation(summary = "从ES删除指定Post")
    public BaseResponse<String> deletePostFromEs(@PathVariable Long id) {
        try {
            postDataSyncService.deletePostFromEs(id);
            return ResultUtils.success("Post从ES删除成功");
        } catch (Exception e) {
            log.error("从ES删除Post失败, ID: {}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败"+ e.getMessage());
        }
    }
}