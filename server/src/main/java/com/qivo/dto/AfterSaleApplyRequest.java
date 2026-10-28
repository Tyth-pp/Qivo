package com.qivo.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record AfterSaleApplyRequest(@NotNull Long orderId,@NotBlank @Size(max=500) String reason) {}
