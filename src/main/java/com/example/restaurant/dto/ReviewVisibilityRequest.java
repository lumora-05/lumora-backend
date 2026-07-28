package com.example.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewVisibilityRequest(
        @NotNull(message = "Trạng thái hiển thị không được để trống")
        Boolean visible
) {
}
