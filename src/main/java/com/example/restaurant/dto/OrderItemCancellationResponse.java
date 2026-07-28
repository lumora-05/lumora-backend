package com.example.restaurant.dto;

import java.time.LocalDateTime;

/** Dữ liệu gọn dành cho danh sách yêu cầu hủy món của phục vụ/admin. */
public record OrderItemCancellationResponse(
        Integer maChiTiet,
        Integer maDonHang,
        Integer maBan,
        String tenBan,
        Integer maMonAn,
        String tenMonAn,
        Integer soLuong,
        String trangThaiMon,
        String trangThaiHuy,
        String trangThaiTruocHuy,
        String maLyDoHuy,
        String lyDoHuy,
        String ghiChuHuy,
        String nguonYeuCauHuy,
        Integer maNguoiYeuCauHuy,
        String tenNguoiYeuCauHuy,
        LocalDateTime thoiGianYeuCauHuy,
        Integer maNguoiXuLyHuy,
        String tenNguoiXuLyHuy,
        LocalDateTime thoiGianXuLyHuy,
        String ghiChuXuLyHuy
) {
}
