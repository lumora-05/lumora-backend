package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueByDayResponse(
        LocalDate ngay,
        BigDecimal doanhThu,
        long soHoaDon
) {
}
