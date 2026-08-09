package com.example.restaurant.dto;

import java.math.BigDecimal;

public record TopSellingFoodResponse(
        Integer maMonAn,
        String tenMonAn,
        BigDecimal gia,
        String moTa,
        String hinhAnh,
        long soLuongBan
) {
}
