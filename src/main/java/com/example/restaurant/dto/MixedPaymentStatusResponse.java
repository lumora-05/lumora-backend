package com.example.restaurant.dto;

import com.example.restaurant.entity.Invoice;

import java.math.BigDecimal;
import java.util.List;

public record MixedPaymentStatusResponse(
        Integer maDonHang,
        BigDecimal tongCanThanhToan,
        BigDecimal daThanhToan,
        BigDecimal tienMatDaThanhToan,
        BigDecimal chuyenKhoanDaThanhToan,
        BigDecimal chuyenKhoanDangCho,
        BigDecimal conLai,
        boolean hoanTat,
        List<MixedPaymentPartResponse> cacKhoanThanhToan,
        Invoice hoaDon
) {
}
