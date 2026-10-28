package com.qivo.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件上传配置 —— 读取 yml 中 qivo.upload 相关参数
 */
@Configuration
@ConfigurationProperties(prefix = "qivo.upload")
public class FileUploadProperties {

    private String path;
    private String allowTypes;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAllowTypes() {
        return allowTypes;
    }

    public void setAllowTypes(String allowTypes) {
        this.allowTypes = allowTypes;
    }
}
