package com.qivo.dto;
import jakarta.validation.constraints.*;
public record AdminResetPasswordRequest(@NotBlank @Size(min=8,max=72) String password) {}
