package com.qivo.dto;

import com.qivo.constant.AccountMessageConstant;
import jakarta.validation.constraints.*;

public record AdminCreateRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = AccountMessageConstant.USERNAME_REQUIREMENTS) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 50) String realName) {}
