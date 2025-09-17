package com.lgk.lgkaicodeservice.datasync;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.Message;
import com.lgk.lgkaicodeservice.config.CanalConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Canal 客户端
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Component
@Slf4j
public class CanalClient {

    @Resource
    private CanalConfig canalConfig;

    @Resource
    private DataSyncProcessor dataSyncProcessor;

    private CanalConnector connector;
    private volatile boolean running = false;

    @PostConstruct
    public void init() {
        if (!canalConfig.getEnabled()) {
            log.info("Canal 同步功能已禁用");
            return;
        }

        try {
            // 创建连接
            connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(canalConfig.getHost(), canalConfig.getPort()),
                    canalConfig.getDestination(),
                    canalConfig.getUsername(),
                    canalConfig.getPassword()
            );

            // 异步启动Canal客户端
            CompletableFuture.runAsync(this::startSync);
            log.info("Canal 客户端初始化成功，开始监听数据变化");
        } catch (Exception e) {
            log.error("Canal 客户端初始化失败", e);
        }
    }

    /**
     * 启动同步
     */
    private void startSync() {
        int emptyCount = 0;
        running = true;

        try {
            connector.connect();
            connector.subscribe(canalConfig.getSubscribe());
            connector.rollback();

            log.info("Canal 客户端连接成功，开始同步数据");

            while (running && emptyCount < canalConfig.getEmptyCount()) {
                try {
                    // 获取指定数量的数据
                    Message message = connector.getWithoutAck(canalConfig.getBatchSize());
                    long batchId = message.getId();
                    int size = message.getEntries().size();

                    if (batchId == -1 || size == 0) {
                        emptyCount++;
                        log.debug("Canal 空数据次数: {}", emptyCount);
                        Thread.sleep(canalConfig.getSleepTime());
                    } else {
                        emptyCount = 0;
                        log.debug("Canal 接收到数据，batchId: {}, size: {}", batchId, size);
                        
                        // 处理数据
                        dataSyncProcessor.processEntries(message.getEntries());
                    }

                    // 提交确认
                    connector.ack(batchId);
                } catch (Exception e) {
                    log.error("Canal 数据处理异常", e);
                    // 发生异常时等待一段时间再继续
                    Thread.sleep(canalConfig.getSleepTime());
                }
            }

            if (emptyCount >= canalConfig.getEmptyCount()) {
                log.warn("Canal 空数据次数过多，停止同步");
            }
        } catch (Exception e) {
            log.error("Canal 同步过程中发生异常", e);
        } finally {
            if (connector != null) {
                connector.disconnect();
            }
            running = false;
            log.info("Canal 客户端已断开连接");
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (connector != null) {
            connector.disconnect();
        }
        log.info("Canal 客户端已销毁");
    }

    /**
     * 重启Canal客户端
     */
    public void restart() {
        log.info("重启 Canal 客户端");
        destroy();
        init();
    }
}