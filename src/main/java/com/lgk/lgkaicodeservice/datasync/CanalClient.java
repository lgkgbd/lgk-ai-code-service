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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private volatile boolean connected = false;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_INTERVAL_MS = 5000; // 5秒重连间隔
    private static final long MAX_RECONNECT_INTERVAL_MS = 60000; // 最大60秒重连间隔

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
        running = true;

        while (running) {
            try {
                // 尝试连接
                if (!connected) {
                    if (!connectToCanal()) {
                        continue; // 连接失败，继续重试
                    }
                }
                
                // 开始数据同步循环
                syncDataLoop();
                
            } catch (Exception e) {
                log.error("Canal 同步过程中发生异常", e);
                handleConnectionError();
            }
        }

        log.info("Canal 客户端同步循环已结束");
    }
    
    /**
     * 连接到Canal服务器
     */
    private boolean connectToCanal() {
        try {
            if (connector != null) {
                try {
                    connector.disconnect();
                } catch (Exception e) {
                    log.debug("断开旧连接时发生异常", e);
                }
            }
            
            // 创建新连接
            connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(canalConfig.getHost(), canalConfig.getPort()),
                    canalConfig.getDestination(),
                    canalConfig.getUsername(),
                    canalConfig.getPassword()
            );
            
            connector.connect();
            connector.subscribe(canalConfig.getSubscribe());
            connector.rollback();
            
            connected = true;
            reconnectAttempts.set(0); // 重置重连计数
            log.info("Canal 客户端连接成功，开始同步数据");
            return true;
            
        } catch (Exception e) {
            connected = false;
            int attempts = reconnectAttempts.incrementAndGet();
            
            if (attempts <= MAX_RECONNECT_ATTEMPTS) {
                long waitTime = Math.min(RECONNECT_INTERVAL_MS * attempts, MAX_RECONNECT_INTERVAL_MS);
                log.warn("Canal 连接失败，第 {} 次重连尝试，{}ms 后重试: {}", attempts, waitTime, e.getMessage());
                
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.info("Canal 重连等待被中断");
                    return false;
                }
            } else {
                log.error("Canal 连接失败，已达到最大重连次数 {}", MAX_RECONNECT_ATTEMPTS, e);
                // 重置计数器，继续尝试
                reconnectAttempts.set(0);
                try {
                    Thread.sleep(MAX_RECONNECT_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }
    
    /**
     * 数据同步循环
     */
    private void syncDataLoop() throws InterruptedException {
        while (running && connected) {
            try {
                // 获取指定数量的数据
                Message message = connector.getWithoutAck(canalConfig.getBatchSize());
                long batchId = message.getId();
                int size = message.getEntries().size();

                if (batchId == -1 || size == 0) {
                    Thread.sleep(canalConfig.getSleepTime());
                } else {
                    log.debug("Canal 接收到数据，batchId: {}, size: {}", batchId, size);
                    
                    // 处理数据
                    dataSyncProcessor.processEntries(message.getEntries());
                }

                // 提交确认
                connector.ack(batchId);
                
            } catch (Exception e) {
                log.error("Canal 数据处理异常: {}", e.getMessage());
                
                // 判断是否为连接相关异常
                if (isConnectionException(e)) {
                    log.warn("检测到连接异常，标记为断开状态");
                    connected = false;
                    throw e; // 抛出异常，触发重连
                } else {
                    // 非连接异常，等待后继续
                    Thread.sleep(canalConfig.getSleepTime());
                }
            }
        }
    }
    
    /**
     * 处理连接错误
     */
    private void handleConnectionError() {
        connected = false;
        if (connector != null) {
            try {
                connector.disconnect();
            } catch (Exception e) {
                log.debug("断开连接时发生异常", e);
            }
        }
        
        if (running) {
            log.info("Canal 连接已断开，准备重新连接...");
            try {
                Thread.sleep(RECONNECT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Canal 重连等待被中断");
            }
        }
    }
    
    /**
     * 判断是否为连接相关异常
     */
    private boolean isConnectionException(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("connection") 
            || lowerMessage.contains("socket") 
            || lowerMessage.contains("timeout")
            || lowerMessage.contains("refused")
            || lowerMessage.contains("reset")
            || e instanceof java.net.SocketException
            || e instanceof java.net.ConnectException
            || e instanceof java.io.IOException;
    }

    @PreDestroy
    public void destroy() {
        log.info("正在关闭 Canal 客户端...");
        running = false;
        connected = false;
        
        if (connector != null) {
            try {
                connector.disconnect();
            } catch (Exception e) {
                log.debug("关闭连接时发生异常", e);
            }
        }
        log.info("Canal 客户端已销毁");
    }

    /**
     * 重启Canal客户端
     */
    public void restart() {
        log.info("重启 Canal 客户端");
        destroy();
        // 等待一段时间确保资源释放
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        init();
    }
    
    /**
     * 获取连接状态
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * 获取运行状态
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 手动触发重连
     */
    public void reconnect() {
        log.info("手动触发 Canal 重连");
        connected = false;
        reconnectAttempts.set(0);
    }
}