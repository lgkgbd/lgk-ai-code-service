package com.lgk.lgkaicodeservice.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);           // 核心线程数
        executor.setMaxPoolSize(10);           // 最大线程数
        executor.setQueueCapacity(500);        // 队列容量
        executor.setThreadNamePrefix("async-"); // 线程名前缀
        executor.setKeepAliveSeconds(60);      // 线程存活时间
        executor.setRejectedExecutionHandler(  // 拒绝策略
                new ThreadPoolExecutor.CallerRunsPolicy()// 效果：被拒绝的任务会在调用线程中执行，不会丢失任务
        );
        executor.setWaitForTasksToCompleteOnShutdown(true); // 等待所有任务结束后再关闭线程池，配合下面的设置等待时间一起使用
        executor.setAwaitTerminationSeconds(60); // 设置等待时间 最多等待60秒

        executor.initialize();                 // 初始化
        return executor;
    }
}
