package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal tongDoanhThu,
        long soHoaDon
) {
}
