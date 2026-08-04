package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyCustomerResponse(
        Integer maKhachHang,
        String hoTen,
        String soDienThoai,
        Integer diemTichLuy,
        BigDecimal tongChiTieu,
        String trangThai,
        LocalDateTime thoiGianTao,
        LocalDateTime thoiGianCapNhat
) {
}
