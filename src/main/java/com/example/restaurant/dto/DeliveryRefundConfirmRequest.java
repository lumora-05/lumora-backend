package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryRefundConfirmRequest(
        @NotBlank @Size(max = 100) String maGiaoDich,
        @Size(max = 500) String ghiChu
) {
}
