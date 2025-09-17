package com.lgk.lgkaicodeservice.datasync;

import com.lgk.lgkaicodeservice.config.CanalConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Canal健康检查指示器
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Component
@Slf4j
public class CanalHealthIndicator implements HealthIndicator {

    @Resource
    private CanalConfig canalConfig;

    @Resource
    private CanalClient canalClient;

    @Override
    public Health health() {
        try {
            if (!canalConfig.getEnabled()) {
                return Health.up()
                        .withDetail("status", "disabled")
                        .withDetail("message", "Canal同步功能已禁用")
                        .build();
            }

            // 这里可以添加更详细的健康检查逻辑
            // 比如检查Canal连接状态、最后同步时间等
            
            return Health.up()
                    .withDetail("status", "running")
                    .withDetail("host", canalConfig.getHost())
                    .withDetail("port", canalConfig.getPort())
                    .withDetail("destination", canalConfig.getDestination())
                    .withDetail("message", "Canal客户端运行正常")
                    .build();
                    
        } catch (Exception e) {
            log.error("Canal健康检查异常", e);
            return Health.down()
                    .withDetail("status", "error")
                    .withDetail("message", "Canal客户端异常: " + e.getMessage())
                    .build();
        }
    }
}