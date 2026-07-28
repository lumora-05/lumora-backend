package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FoodRequest(
        @NotNull Integer maDanhMuc,
        @NotBlank String tenMonAn,
        @NotNull @DecimalMin("0") BigDecimal gia,
        String moTa,
        String hinhAnh,
        Boolean trangThai
) {
}
