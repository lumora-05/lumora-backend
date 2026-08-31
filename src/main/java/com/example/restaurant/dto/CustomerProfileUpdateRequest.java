package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerProfileUpdateRequest(
        @NotBlank(message = "Họ tên không được bỏ trống")
        @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
        String hoTen,

        @NotBlank(message = "Số điện thoại không được bỏ trống")
        @Pattern(regexp = "^[0-9+ .()-]{9,20}$", message = "Số điện thoại không hợp lệ")
        String soDienThoai
) {
}
