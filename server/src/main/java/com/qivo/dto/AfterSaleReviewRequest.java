package com.qivo.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record AfterSaleReviewRequest(@NotNull Boolean approve,@Size(max=500) String note) {}
