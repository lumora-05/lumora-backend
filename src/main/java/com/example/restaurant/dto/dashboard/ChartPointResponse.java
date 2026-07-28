package com.example.restaurant.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChartPointResponse(
        LocalDate ngay,
        BigDecimal doanhThu,
        long soHoaDon,
        long soDonHang
) {
}
