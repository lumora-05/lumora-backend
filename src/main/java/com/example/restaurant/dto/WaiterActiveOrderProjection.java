package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection phẳng, nhẹ cho danh sách đơn đang hoạt động của phục vụ.
 * Mỗi dòng tương ứng với một chi tiết món (hoặc một dòng rỗng nếu đơn chưa có món).
 */
public interface WaiterActiveOrderProjection {
    Integer getMaDonHang();

    Integer getMaBan();

    String getTenBan();

    String getTrangThai();

    LocalDateTime getThoiGianDat();

    LocalDateTime getThoiGianCapNhat();

    LocalDateTime getThoiGianSanSang();

    LocalDateTime getThoiGianYeuCauThanhToan();

    BigDecimal getTongTien();

    String getMaNhomThanhToan();

    Integer getMaChiTiet();

    Integer getSoLuong();

    String getTrangThaiMon();

    String getTenMonAn();

    Integer getLanGoi();

    LocalDateTime getThoiGianThem();
}
