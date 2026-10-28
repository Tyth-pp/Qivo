package com.qivo.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.Data;

/** 事务内写入的历史快照；角色删除后仍保留记录。 */
@Data
public class RoleChange {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long roleId;
    private String roleName;
    private String action;
    private Long actorId;
    private String actorName;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String note;
    private LocalDateTime createTime;
}
