package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminCustomerStatusRequest(
        @NotBlank(message = "Trạng thái không được để trống")
        @Pattern(regexp = "(?i)HOAT_DONG|KHOA", message = "Trạng thái chỉ được là HOAT_DONG hoặc KHOA")
        String trangThai
) {
}
