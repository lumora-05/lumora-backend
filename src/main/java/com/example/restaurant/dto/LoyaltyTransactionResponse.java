package com.example.restaurant.dto;

import java.time.LocalDateTime;

public record LoyaltyTransactionResponse(
        Long maGiaoDichDiem,
        Integer maKhachHang,
        Integer maDonHang,
        String loaiGiaoDich,
        Integer soDiem,
        Integer soDuSauGiaoDich,
        String noiDung,
        LocalDateTime thoiGian
) {
}
