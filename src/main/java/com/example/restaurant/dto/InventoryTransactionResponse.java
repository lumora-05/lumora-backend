package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryTransactionResponse(
        Long maGiaoDich,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String donViTinh,
        Long maLo,
        String soLo,
        LocalDate hanSuDung,
        String loaiGiaoDich,
        BigDecimal soLuong,
        BigDecimal soLuongTruoc,
        BigDecimal soLuongSau,
        BigDecimal donGiaNhap,
        BigDecimal giaTriGiaoDich,
        String lyDo,
        String maLyDo,
        String ghiChu,
        String nguoiThucHien,
        LocalDateTime thoiGian
) {
}
