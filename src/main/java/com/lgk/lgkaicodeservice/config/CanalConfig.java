package com.lgk.lgkaicodeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Canal 配置类
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Configuration
@ConfigurationProperties(prefix = "canal")
@Data
public class CanalConfig {

    /**
     * Canal 服务器地址
     */
    private String host = "localhost";

    /**
     * Canal 服务器端口
     */
    private Integer port = 11111;

    /**
     * Canal 实例名称
     */
    private String destination = "example";

    /**
     * 用户名
     */
    private String username = "";

    /**
     * 密码
     */
    private String password = "";

    /**
     * 订阅的数据库表规则
     */
    private String subscribe = ".*\\..*";

    /**
     * 批量获取数据大小
     */
    private Integer batchSize = 1000;

    /**
     * 是否启用Canal同步
     */
    private Boolean enabled = true;

    /**
     * 空数据等待时间（毫秒）
     */
    private Long sleepTime = 1000L;
}