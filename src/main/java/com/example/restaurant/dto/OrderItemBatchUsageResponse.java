package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrderItemBatchUsageResponse(
        Long maSuDung,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String donViTinh,
        Long maLo,
        String soLo,
        LocalDate ngayNhap,
        LocalDate ngaySanXuat,
        LocalDate hanSuDung,
        String nhaCungCap,
        String trangThaiAnToan,
        BigDecimal soLuongSuDung,
        String trangThai,
        String nguoiCapPhat,
        LocalDateTime thoiGianCapPhat
) {
}
