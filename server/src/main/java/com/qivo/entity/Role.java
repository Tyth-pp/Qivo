package com.qivo.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色表
 */
@Data
public class Role implements Serializable {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Long version;
    private long memberCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
