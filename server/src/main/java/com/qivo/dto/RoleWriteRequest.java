package com.qivo.dto;

import jakarta.validation.constraints.*;

public record RoleWriteRequest(
        Long id,
        @NotBlank(message = "请填写角色名称")
        @Size(max = 50, message = "角色名称最多50个字符") String name,
        @NotBlank(message = "请填写角色编码")
        @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{2,49}",
                message = "角色编码使用3至50位字母、数字或下划线，并以字母开头") String code,
        @Size(max = 255, message = "角色说明最多255个字符") String description,
        @PositiveOrZero Long version) {}
