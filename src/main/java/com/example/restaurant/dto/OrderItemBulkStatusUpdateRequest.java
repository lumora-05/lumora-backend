package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cập nhật trạng thái nhiều món trong một lần gọi API của bếp.
 * Giới hạn số lượng để tránh một request vô tình khóa quá nhiều đơn cùng lúc.
 */
public record OrderItemBulkStatusUpdateRequest(
        @NotEmpty(message = "Danh sách món không được để trống")
        @Size(max = 200, message = "Chỉ được cập nhật tối đa 200 món mỗi lần")
        List<@NotNull Integer> itemIds,
        @NotBlank String trangThaiMon
) {
}
