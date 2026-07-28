package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * maNhanVien được giữ để tương thích frontend cũ nhưng backend không dùng giá
 * trị này để gán người xử lý. Danh tính nhân viên luôn lấy từ JWT.
 */
public record OrderStatusUpdateRequest(
        @NotBlank String trangThai,
        Integer maNhanVien
) {
}
