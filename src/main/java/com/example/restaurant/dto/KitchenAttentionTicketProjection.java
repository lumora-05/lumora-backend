package com.example.restaurant.dto;

/**
 * Một phiếu bếp được nhận diện theo mã đơn + lần gọi.
 * Projection này chỉ dùng cho endpoint count, tránh tải toàn bộ đơn/món.
 */
public interface KitchenAttentionTicketProjection {
    Integer getMaDonHang();

    Integer getLanGoi();
}
