package com.qivo.dto;

import jakarta.validation.constraints.Size;

public record AdminProfileRequest(@Size(max = 50) String realName, @Size(max = 255) String avatar) {}
