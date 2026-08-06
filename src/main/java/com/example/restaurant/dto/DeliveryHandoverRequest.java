package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeliveryHandoverRequest(
        @NotBlank @Size(max = 120) String donViVanChuyen,
        @NotBlank @Size(max = 120) String tenNguoiGiao,
        @NotBlank
        @Pattern(regexp = "^[0-9+ .()-]{9,20}$", message = "Số điện thoại người giao không hợp lệ")
        String soDienThoaiNguoiGiao,
        @Size(max = 500) String ghiChuBanGiao
) {
}
