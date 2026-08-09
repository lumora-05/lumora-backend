package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeliveryOtpRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9+ .()-]{9,20}$", message = "Số điện thoại không hợp lệ")
        String soDienThoai
) {
}
