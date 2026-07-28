package com.example.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForgotPasswordVerifyCodeRequest(
        @NotBlank(message = "Email không được bỏ trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        String email,

        @NotBlank(message = "Mã xác nhận không được bỏ trống")
        @Pattern(regexp = "^\\d{6}$", message = "Mã xác nhận phải gồm đúng 6 chữ số")
        String code
) {
}
