package com.example.restaurant.dto;

import java.time.LocalDateTime;

public record ReservationResponse(
        Integer maDatBan,
        String maTraCuu,
        String hoTenKhach,
        String soDienThoai,
        LocalDateTime ngayGioDen,
        LocalDateTime thoiGianKetThucDuKien,
        Integer thoiLuongPhut,
        Integer soLuongKhach,
        String khuVucMongMuon,
        Integer maBanDuKien,
        String tenBanDuKien,
        Integer maBanThucTe,
        String tenBanThucTe,
        Integer maDonHang,
        String ghiChu,
        String trangThai,
        String lyDoHuyTuChoi,
        LocalDateTime thoiGianTao,
        LocalDateTime thoiGianCapNhat,
        LocalDateTime thoiGianXacNhan,
        LocalDateTime thoiGianCheckIn,
        LocalDateTime thoiGianXepBan,
        LocalDateTime thoiGianHoanThanh,
        Integer maNguoiXacNhan,
        String tenNguoiXacNhan,
        Integer maNguoiCheckIn,
        String tenNguoiCheckIn,
        Integer maNguoiXepBan,
        String tenNguoiXepBan
) {}
