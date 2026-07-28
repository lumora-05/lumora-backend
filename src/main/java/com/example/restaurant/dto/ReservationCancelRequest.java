package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationCancelRequest(
        @NotBlank(message = "Lý do hủy không được để trống")
        @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
        String reason
) {}
