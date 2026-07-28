package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IngredientResponse(
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String donViTinh,
        BigDecimal soLuongTon,
        BigDecimal soLuongTonVatLy,
        BigDecimal soLuongKhaDung,
        BigDecimal soLuongChoTieuHuy,
        BigDecimal mucTonToiThieu,
        BigDecimal giaNhap,
        String moTa,
        Boolean trangThai,
        String trangThaiTonKho,
        BigDecimal giaTriTon,
        BigDecimal giaTriTonKhaDung,
        BigDecimal giaTriChoTieuHuy,
        LocalDateTime ngayTao,
        LocalDateTime ngayCapNhat
) {
}
