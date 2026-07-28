package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminNotificationResponse(
        Long maThongBao,
        String loaiThongBao,
        String tieuDe,
        String noiDung,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        BigDecimal soLuongTon,
        BigDecimal mucTonToiThieu,
        String donViTinh,
        String trangThaiTonKho,
        Boolean daDoc,
        LocalDateTime thoiGianTao,
        LocalDateTime thoiGianDoc
) {
}
