package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection nhẹ cho hàng chờ thanh toán của thu ngân.
 * Chỉ lấy các trường thật sự cần trên màn hình danh sách để tránh tải toàn bộ Order graph.
 */
public interface CashierPaymentRequestProjection {
    Integer getMaDonHang();

    Integer getMaBan();

    String getTenBan();

    Integer getMaBanChinh();

    Long getSoMon();

    BigDecimal getTongTien();

    String getTrangThai();

    LocalDateTime getThoiGianYeuCauThanhToan();

    LocalDateTime getThoiGianCapNhat();

    LocalDateTime getThoiGianDat();

    String getMaNhomThanhToan();
}
