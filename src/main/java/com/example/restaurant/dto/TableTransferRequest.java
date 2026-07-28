package com.example.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public record TableTransferRequest(
        @NotNull(message = "Mã bàn đích không được để trống")
        Integer maBanDich
) {
}
