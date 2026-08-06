package com.example.restaurant.dto;

import java.math.BigDecimal;

public record DeliveryOrderCreateResponse(
        Integer maDonHang,
        String maDonHangHienThi,
        String trackingToken,
        String trangThaiGiaoHang,
        String phuongThucThanhToan,
        String trangThaiThanhToan,
        BigDecimal tamTinh,
        BigDecimal tienGiam,
        BigDecimal phiGiaoHang,
        BigDecimal tongThanhToan
) {
}
