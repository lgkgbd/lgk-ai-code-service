package com.lgk.lgkaicodeservice.datasync;

import com.lgk.lgkaicodeservice.config.CanalConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 数据同步初始化器
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Component
@Slf4j
public class DataSyncInitializer {

    @Resource
    private CanalConfig canalConfig;

    @Resource
    private PostDataSyncService postDataSyncService;

    /**
     * 应用启动完成后执行初始化
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!canalConfig.getEnabled()) {
            log.info("Canal数据同步功能已禁用，跳过初始化");
            return;
        }

        log.info("开始初始化数据同步模块");
        
        try {
            // 可选：应用启动时进行一次全量同步
            // 注释掉下面的代码如果不需要启动时全量同步
            /*
            log.info("执行启动时全量同步...");
            long syncCount = postDataSyncService.syncAllPostsToEs();
            log.info("启动时全量同步完成，同步数据条数: {}", syncCount);
            */
            
            log.info("数据同步模块初始化完成");
        } catch (Exception e) {
            log.error("数据同步模块初始化失败", e);
        }
    }
}