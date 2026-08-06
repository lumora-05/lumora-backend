package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DeliveryTrackingResponse(
        Integer maDonHang,
        String maDonHangHienThi,
        String trackingToken,
        String maVanChuyen,
        String trangThaiDonHang,
        String trangThaiGiaoHang,
        String tenNguoiNhan,
        String soDienThoaiNhanChe,
        String diaChiGiaoHang,
        String khuVucGiaoHang,
        String ghiChuGiaoHang,
        String phuongThucThanhToan,
        String trangThaiThanhToan,
        BigDecimal soTienDaThanhToan,
        String donViVanChuyen,
        String tenNguoiGiao,
        String soDienThoaiNguoiGiaoChe,
        BigDecimal tamTinh,
        BigDecimal tienGiam,
        BigDecimal phiGiaoHang,
        BigDecimal tongThanhToan,
        LocalDateTime thoiGianDat,
        LocalDateTime thoiGianXacNhan,
        LocalDateTime thoiGianSanSang,
        LocalDateTime thoiGianBanGiao,
        LocalDateTime thoiGianGiaoThanhCong,
        String lyDoTuChoi,
        String lyDoGiaoThatBai,
        List<DeliveryTrackingItemResponse> items
) {
}
