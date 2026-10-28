package com.qivo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动类
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.qivo.mapper")
@EnableScheduling
public class QivoApplication {

    public static void main(String[] args) {
        SpringApplication.run(QivoApplication.class, args);
    }
}
