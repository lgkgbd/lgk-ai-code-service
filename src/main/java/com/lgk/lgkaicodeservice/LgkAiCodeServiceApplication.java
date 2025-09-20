package com.lgk.lgkaicodeservice;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

//排除 embedding 的自动装配，本项目用不到
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@EnableCaching
@MapperScan("com.lgk.lgkaicodeservice.mapper")
@EnableAsync
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true) //启用 Spring 的 AspectJ 自动代理功能； 强制使用 CGLIB 代理而不是 JDK 动态代理；将当前代理对象暴露到 AopContext 中，允许在目标对象中访问代理对象
public class LgkAiCodeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LgkAiCodeServiceApplication.class, args);
    }

}
