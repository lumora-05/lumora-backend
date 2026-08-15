package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FoodRequest(
        @NotNull Integer maDanhMuc,
        @NotBlank @Size(max = 100) String tenMonAn,
        @Size(max = 150) String tenMonAnEn,
        @NotNull @DecimalMin("0") BigDecimal gia,
        @Size(max = 255) String moTa,
        @Size(max = 500) String moTaEn,
        String hinhAnh,
        Boolean trangThai
) {
}
