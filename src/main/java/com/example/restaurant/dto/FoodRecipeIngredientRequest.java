package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FoodRecipeIngredientRequest(
        @NotNull Integer maNguyenLieu,
        @NotNull @DecimalMin(value = "0.001", message = "Định lượng phải lớn hơn 0") BigDecimal dinhLuong
) {
}
