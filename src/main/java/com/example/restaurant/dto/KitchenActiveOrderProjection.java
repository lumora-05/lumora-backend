package com.example.restaurant.dto;

import java.time.LocalDateTime;

/**
 * Projection phẳng, nhẹ cho Bảng chế biến của bếp.
 * Mỗi dòng tương ứng với một chi tiết món thuộc đơn mà bếp đang được phép nhìn thấy.
 */
public interface KitchenActiveOrderProjection {
    Integer getMaDonHang();

    Integer getMaBan();

    String getTenBan();

    String getTrangThai();

    LocalDateTime getThoiGianDat();

    String getGhiChuDon();

    String getLoaiDon();

    String getPhuongThucNhanHang();

    Integer getMaChiTiet();

    Integer getSoLuong();

    String getGhiChuMon();

    String getTrangThaiMon();

    Integer getLanGoi();

    LocalDateTime getThoiGianThem();

    Integer getMaMonAn();

    String getTenMonAn();
}
