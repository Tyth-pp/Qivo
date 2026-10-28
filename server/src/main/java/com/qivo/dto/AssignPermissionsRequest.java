package com.qivo.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record AssignPermissionsRequest(
        @NotNull @Positive Long roleId,
        @NotNull @Size(max = 200) List<@NotNull @Positive Long> permissionIds,
        @NotNull(message = "请刷新角色信息后重试") @PositiveOrZero Long version) {}
