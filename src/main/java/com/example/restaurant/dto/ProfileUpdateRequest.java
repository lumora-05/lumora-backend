package com.example.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Họ và tên không được bỏ trống")
        @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
        String hoTen,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        String email,

        @Pattern(
                regexp = "^$|^[0-9+\\s.-]{8,15}$",
                message = "Số điện thoại không hợp lệ"
        )
        String soDienThoai
) {
}
