package com.example.restaurant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Xác nhận nhiều món đã được phục vụ trong một lần gọi API.
 * Giới hạn số lượng để tránh một request vô tình khóa quá nhiều đơn cùng lúc.
 */
public record OrderItemBulkServedRequest(
        @NotEmpty(message = "Danh sách món không được để trống")
        @Size(max = 200, message = "Chỉ được xác nhận tối đa 200 món mỗi lần")
        List<@NotNull Integer> itemIds
) {
}
