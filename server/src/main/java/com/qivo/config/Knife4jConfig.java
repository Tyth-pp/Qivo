package com.qivo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API 文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI qivoOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("栖物 Qivo 商品交易与运营平台 API 文档")
                                .description("基于 Spring Boot 3 + Vue 3 栖物 Qivo 商品交易与运营平台接口文档")
                                .version("1.0.0")
                                .contact(new Contact().name("Qivo")));
    }
}
