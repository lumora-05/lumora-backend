package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MixedPaymentPartResponse(
        Long maKhoanThanhToan,
        String phuongThucThanhToan,
        BigDecimal soTien,
        String maGiaoDich,
        LocalDateTime thoiGianThanhToan
) {
}
