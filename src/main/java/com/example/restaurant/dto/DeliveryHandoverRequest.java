package com.example.restaurant.dto;

import jakarta.validation.constraints.Size;

/**
 * Tài xế/đơn vị vận chuyển được hệ thống đối tác mô phỏng điều phối tự động.
 * Các trường cũ vẫn được giữ để tương thích frontend cũ nhưng backend không dùng
 * chúng để thay đổi tài xế đã được điều phối.
 */
public record DeliveryHandoverRequest(
        @Size(max = 120) String donViVanChuyen,
        @Size(max = 120) String tenNguoiGiao,
        @Size(max = 20) String soDienThoaiNguoiGiao,
        @Size(max = 500) String ghiChuBanGiao
) {
}
