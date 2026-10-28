package com.qivo.config;

import com.qivo.interceptor.RepeatSubmitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置 —— 拦截器注册、静态资源映射
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadPath;

    private final RepeatSubmitInterceptor repeatSubmitInterceptor;

    public WebMvcConfig(
            RepeatSubmitInterceptor repeatSubmitInterceptor,
            @Value("${qivo.upload.path}") String uploadPath) {
        this.repeatSubmitInterceptor = repeatSubmitInterceptor;
        this.uploadPath = uploadPath;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 防重复提交拦截器
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传文件的静态资源映射
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath)
                .setCacheControl(CacheControl.noStore());
    }
}
