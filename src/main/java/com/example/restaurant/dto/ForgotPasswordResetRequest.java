package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForgotPasswordResetRequest(
        @NotBlank(message = "Mã đặt lại mật khẩu không được bỏ trống")
        String resetToken,

        @NotBlank(message = "Mật khẩu mới không được bỏ trống")
        @Size(min = 8, max = 72, message = "Mật khẩu mới phải có từ 8 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=\\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Mật khẩu mới phải có chữ hoa, chữ thường, số và ký tự đặc biệt, đồng thời không chứa khoảng trắng"
        )
        String matKhauMoi,

        @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
        String xacNhanMatKhauMoi
) {
}
