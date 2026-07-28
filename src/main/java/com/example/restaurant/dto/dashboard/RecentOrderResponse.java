package com.example.restaurant.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecentOrderResponse(
        Integer maDonHang,
        String tenBan,
        BigDecimal tongTien,
        String trangThai,
        LocalDateTime thoiGianDat
) {
}
