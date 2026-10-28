package com.qivo.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限表
 */
@Data
public class Permission implements Serializable {

    private Long id;
    private String name;
    private String code;
    private String url;
    private Long parentId;
    private Integer type; // 1=菜单 2=按钮 3=接口
    private Integer sortOrder;
    private LocalDateTime createTime;
}
