package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeliveryOtpVerifyRequest(
        @NotBlank @Size(max = 100) String requestId,
        @NotBlank
        @Pattern(regexp = "^[0-9+ .()-]{9,20}$", message = "Số điện thoại không hợp lệ")
        String soDienThoai,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "OTP phải gồm 6 chữ số") String otp
) {
}
