package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApplyPromotionRequest(
        @NotNull Integer maDonHang,
        @NotBlank String maCode
) {
}
