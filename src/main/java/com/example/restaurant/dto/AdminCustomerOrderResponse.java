package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCustomerOrderResponse(
        Integer maDonHang,
        String loaiDon,
        String nguonDon,
        String trangThai,
        BigDecimal tongTien,
        LocalDateTime thoiGianDat,
        LocalDateTime thoiGianCapNhat
) {
}
