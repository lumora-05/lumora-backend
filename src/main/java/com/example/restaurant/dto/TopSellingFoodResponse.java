package com.example.restaurant.dto;

import java.math.BigDecimal;

public record TopSellingFoodResponse(
        Integer maMonAn,
        String tenMonAn,
        String tenMonAnEn,
        BigDecimal gia,
        String moTa,
        String moTaEn,
        String hinhAnh,
        long soLuongBan
) {
}
