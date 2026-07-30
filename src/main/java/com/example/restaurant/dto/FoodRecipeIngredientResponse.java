package com.example.restaurant.dto;

import java.math.BigDecimal;

public record FoodRecipeIngredientResponse(
        Long maCongThuc,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String donViTinh,
        BigDecimal dinhLuong,
        Boolean trangThai
) {
}
