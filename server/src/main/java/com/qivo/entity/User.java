package com.qivo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 买家用户表
 */
@Data
public class User implements Serializable {

    private Long id;
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String email;
    private String phone;
    private String avatar;
    private Integer score;
    private Integer status; // 1=正常 0=禁用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
