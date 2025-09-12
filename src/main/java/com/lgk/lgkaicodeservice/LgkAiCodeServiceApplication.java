package com.lgk.lgkaicodeservice;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

//排除 embedding 的自动装配，本项目用不到
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@EnableCaching
@MapperScan("com.lgk.lgkaicodeservice.mapper")
@EnableAsync
public class LgkAiCodeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LgkAiCodeServiceApplication.class, args);
    }

}
