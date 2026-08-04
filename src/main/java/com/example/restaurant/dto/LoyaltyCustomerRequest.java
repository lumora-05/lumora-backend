package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoyaltyCustomerRequest(
        @NotBlank(message = "Họ tên khách hàng không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String hoTen,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String soDienThoai
) {
}
