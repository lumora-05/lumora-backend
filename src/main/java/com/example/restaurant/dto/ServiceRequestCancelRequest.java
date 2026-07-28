package com.example.restaurant.dto;

import jakarta.validation.constraints.Size;

/** Lý do hủy một yêu cầu phục vụ đang mở. */
public record ServiceRequestCancelRequest(
        @Size(max = 500) String lyDo
) {
}
