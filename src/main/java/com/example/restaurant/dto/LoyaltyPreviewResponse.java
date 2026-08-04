package com.example.restaurant.dto;

import java.math.BigDecimal;

public record LoyaltyPreviewResponse(
        Integer maKhachHang,
        String hoTen,
        String soDienThoai,
        boolean khachHangMoi,
        Integer diemHienCo,
        Integer diemToiThieuDeDoi,
        Integer diemToiDaCoTheDung,
        Integer diemSuDung,
        BigDecimal giaTriMotDiem,
        BigDecimal tongTienTruocDiem,
        BigDecimal tienGiamTuDiem,
        BigDecimal tongThanhToan,
        Integer diemDuKienCong,
        Integer diemConLaiSauThanhToan
) {
}
