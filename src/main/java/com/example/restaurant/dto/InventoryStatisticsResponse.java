package com.example.restaurant.dto;

import java.math.BigDecimal;

public record InventoryStatisticsResponse(
        long tongNguyenLieu,
        long dangHoatDong,
        long sapHet,
        long hetHang,
        BigDecimal tongGiaTriTonKho,
        BigDecimal tongGiaTriTonKhaDung,
        BigDecimal tongGiaTriChoTieuHuy,
        long soNguyenLieuCoHangChoTieuHuy,
        long soLoChoTieuHuy
) {
}
