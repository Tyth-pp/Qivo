package com.qivo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "当前管理员实际权限，不包含密码或令牌")
public record AdminAccessVO(
        Long adminId,
        String username,
        @Schema(description = "本次请求从真实角色关联中解析的授权标识") List<String> permissions,
        @Schema(description = "与后台接口规则一致的可用操作，非新增权限码") Map<String, Boolean> capabilities) {}
