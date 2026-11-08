package com.chuan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口。
 */
@EnableScheduling
@SpringBootApplication
public class ChuanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChuanApplication.class, args);
    }
}