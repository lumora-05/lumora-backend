package com.example.restaurant.dto;

import java.math.BigDecimal;

/** Một dòng món ăn trên phiếu tạm tính. */
public record PaymentSlipItemResponse(
        Integer maChiTiet,
        Integer maMonAn,
        String tenMonAn,
        String moTa,
        String hinhAnh,
        Integer soLuong,
        BigDecimal donGia,
        BigDecimal thanhTien,
        String ghiChu
) {
}
