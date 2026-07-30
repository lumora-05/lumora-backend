package com.example.restaurant.dto;

import java.time.LocalDateTime;

public record BatchIncidentResponse(
        Long maSuCo,
        Long maLo,
        Integer maNguyenLieu,
        String tenNguyenLieu,
        String soLo,
        String loaiSuCo,
        String mucDo,
        String lyDo,
        String ghiChu,
        String trangThai,
        String trangThaiAnToanLo,
        String nguoiPhatHien,
        LocalDateTime thoiGianPhatHien,
        String nguoiXuLy,
        LocalDateTime thoiGianXuLy,
        String ketQuaXuLy
) {
}
