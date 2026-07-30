package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BatchImpactItemResponse(
        Long maSuDung,
        Integer maChiTiet,
        Integer maDonHang,
        Integer maBan,
        String tenBan,
        Integer maMonAn,
        String tenMonAn,
        Integer soLuongMon,
        BigDecimal soLuongLoDaDung,
        String donViTinh,
        String trangThaiMon,
        String trangThaiDon,
        LocalDateTime thoiGianDat,
        LocalDateTime thoiGianCapPhat
) {
}
