package com.qivo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot 容器启动测试
 */
@SpringBootTest
@ActiveProfiles("test")
class QivoApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring 容器能正常启动
    }
}
