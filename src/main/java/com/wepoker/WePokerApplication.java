package com.wepoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * WePoker Texas Hold'em Backend
 * 
 * 主应用程序入口
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = "com.wepoker")
public class WePokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WePokerApplication.class, args);
    }
}
