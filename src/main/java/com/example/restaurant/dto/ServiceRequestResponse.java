package com.example.restaurant.dto;

import java.time.LocalDateTime;

/** Dữ liệu yêu cầu phục vụ dùng chung cho khách, phục vụ và admin. */
public record ServiceRequestResponse(
        Integer maYeuCau,
        Integer maBan,
        String tenBan,
        String khuVuc,
        String loaiYeuCau,
        String tenLoaiYeuCau,
        String noiDung,
        String trangThai,
        String mucDoUuTien,
        Integer maNhanVienTiepNhan,
        String tenNhanVienTiepNhan,
        LocalDateTime thoiGianTao,
        LocalDateTime thoiGianTiepNhan,
        LocalDateTime thoiGianHoanThanh,
        LocalDateTime thoiGianHuy,
        Integer maNguoiHuy,
        String tenNguoiHuy,
        String nguonHuy,
        String lyDoHuy,
        long soPhutCho,
        boolean quaHan
) {
}
