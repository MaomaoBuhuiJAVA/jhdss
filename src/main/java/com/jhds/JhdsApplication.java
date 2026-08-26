package com.jhds;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.jhds.mapper")
public class JhdsApplication {
    public static void main(String[] args) {
        SpringApplication.run(JhdsApplication.class, args);
    }
}
