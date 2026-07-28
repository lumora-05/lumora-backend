package com.example.restaurant.dto.dashboard;

import java.math.BigDecimal;

public record TopFoodResponse(
        Integer maMonAn,
        String tenMonAn,
        String hinhAnh,
        long soLuongBan,
        BigDecimal doanhThu
) {
}
