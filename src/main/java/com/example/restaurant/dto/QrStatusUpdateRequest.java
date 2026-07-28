package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record QrStatusUpdateRequest(
        @NotBlank(message = "Trạng thái QR không được để trống")
        String trangThaiQr
) {
}
