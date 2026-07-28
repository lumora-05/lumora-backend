package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu chọn lý do khi yêu cầu hoặc thực hiện hủy món.
 * maLyDo dùng mã cố định để frontend hiển thị danh sách lựa chọn ổn định.
 */
public record OrderItemCancellationRequest(
        @NotBlank String maLyDo,
        @Size(max = 255) String ghiChu
) {
}
