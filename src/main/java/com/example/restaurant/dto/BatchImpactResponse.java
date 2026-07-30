package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BatchImpactResponse(
        Long maLo,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String donViTinh,
        String soLo,
        String nhaCungCap,
        LocalDate ngayNhap,
        LocalDate hanSuDung,
        String trangThaiAnToan,
        BigDecimal soLuongConLai,
        BigDecimal tongSoLuongDaTruyXuat,
        long soMonBiAnhHuong,
        long soDonBiAnhHuong,
        long soBanBiAnhHuong,
        long soMonDangCheBien,
        long soMonDaPhucVu,
        List<BatchImpactItemResponse> chiTietAnhHuong
) {
}
