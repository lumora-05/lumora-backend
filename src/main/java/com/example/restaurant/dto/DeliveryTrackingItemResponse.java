package com.example.restaurant.dto;

import java.math.BigDecimal;

public record DeliveryTrackingItemResponse(
        Integer maMonAn,
        String tenMonAn,
        String tenMonAnEn,
        String hinhAnh,
        String ghiChu,
        Integer soLuong,
        Integer soLuongChoBep,
        Integer soLuongDangCheBien,
        Integer soLuongHoanThanh,
        Integer soLuongDaHuy,
        BigDecimal donGia,
        BigDecimal thanhTien
) {
}
