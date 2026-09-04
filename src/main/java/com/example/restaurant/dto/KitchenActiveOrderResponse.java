package com.example.restaurant.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dữ liệu tối giản cho Bảng chế biến/Thông báo bếp.
 * Giữ cấu trúc banAn, giaoHang, chiTietDonHang và monAn tương thích với helper frontend hiện tại,
 * nhưng không serialize toàn bộ graph Order -> Employee/Promotion/Customer/Food/Category.
 */
public record KitchenActiveOrderResponse(
        Integer maDonHang,
        TableSummary banAn,
        String trangThai,
        LocalDateTime thoiGianDat,
        String ghiChu,
        String loaiDon,
        DeliverySummary giaoHang,
        List<ItemSummary> chiTietDonHang
) {
    public record TableSummary(
            Integer maBan,
            String tenBan
    ) {
    }

    public record DeliverySummary(
            String phuongThucNhanHang
    ) {
    }

    public record FoodSummary(
            Integer maMonAn,
            String tenMonAn
    ) {
    }

    public record ItemSummary(
            Integer maChiTiet,
            FoodSummary monAn,
            Integer soLuong,
            String ghiChu,
            String trangThaiMon,
            Integer lanGoi,
            LocalDateTime thoiGianThem
    ) {
    }
}
