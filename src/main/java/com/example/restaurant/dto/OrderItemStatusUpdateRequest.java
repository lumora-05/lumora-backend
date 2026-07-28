package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderItemStatusUpdateRequest(
        @NotBlank String trangThaiMon
) {
}
