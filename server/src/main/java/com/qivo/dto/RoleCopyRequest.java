package com.qivo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RoleCopyRequest(
        @NotNull @PositiveOrZero Long sourceVersion,
        @NotNull @Valid RoleWriteRequest role) {}
