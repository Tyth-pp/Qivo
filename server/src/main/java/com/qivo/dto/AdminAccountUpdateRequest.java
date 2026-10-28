package com.qivo.dto;
import jakarta.validation.constraints.*;
public record AdminAccountUpdateRequest(@Size(max=50) String realName,Long roleId) {}
