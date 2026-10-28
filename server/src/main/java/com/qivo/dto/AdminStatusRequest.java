package com.qivo.dto;
import jakarta.validation.constraints.*;
public record AdminStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {}
