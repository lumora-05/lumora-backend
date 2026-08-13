package com.example.restaurant.dto;

/**
 * Kết quả trả về từ hệ thống vận chuyển bên ngoài (hoặc bản mô phỏng trong đồ án).
 * LUMORA chỉ lưu kết quả điều phối, không quản lý tài khoản tài xế.
 */
public record DeliveryProviderAssignment(
        String maVanDon,
        String donViVanChuyen,
        String tenTaiXe,
        String soDienThoaiTaiXe
) {
}
