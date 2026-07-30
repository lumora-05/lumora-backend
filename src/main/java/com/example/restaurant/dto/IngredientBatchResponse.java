package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record IngredientBatchResponse(
        Long maLo,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String donViTinh,
        String soLo,
        LocalDate ngayNhap,
        LocalDate ngaySanXuat,
        LocalDate hanSuDung,
        BigDecimal soLuongBanDau,
        BigDecimal soLuongConLai,
        BigDecimal soLuongKhaDung,
        BigDecimal soLuongChoTieuHuy,
        BigDecimal donGiaNhap,
        BigDecimal giaTriConLai,
        BigDecimal giaTriKhaDung,
        BigDecimal giaTriChoTieuHuy,
        String nhaCungCap,
        Boolean trangThai,
        String trangThaiAnToan,
        String trangThaiHanSuDung,
        Long soNgayConLai,
        boolean choPhepXuat,
        boolean choPhepTieuHuy,
        LocalDateTime ngayTao,
        LocalDateTime ngayCapNhat
) {
}
