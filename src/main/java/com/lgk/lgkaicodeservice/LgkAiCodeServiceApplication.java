package com.lgk.lgkaicodeservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lgk.lgkaicodeservice.mapper")
public class LgkAiCodeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LgkAiCodeServiceApplication.class, args);
    }

}
