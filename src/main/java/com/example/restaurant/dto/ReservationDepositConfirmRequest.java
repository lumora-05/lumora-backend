package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationDepositConfirmRequest(
        @NotBlank(message = "Mã giao dịch cọc không được để trống")
        @Size(min = 4, max = 100, message = "Mã giao dịch cọc phải từ 4 đến 100 ký tự")
        String maGiaoDich
) {}
