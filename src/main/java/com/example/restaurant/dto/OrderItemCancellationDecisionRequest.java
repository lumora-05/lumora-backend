package com.example.restaurant.dto;

import jakarta.validation.constraints.Size;

/** Ghi chú của người duyệt hoặc từ chối yêu cầu hủy món. */
public record OrderItemCancellationDecisionRequest(
        @Size(max = 255) String ghiChu
) {
}
