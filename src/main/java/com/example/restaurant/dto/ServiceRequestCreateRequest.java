package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dữ liệu khách gửi khi cần nhân viên hỗ trợ tại bàn. */
public record ServiceRequestCreateRequest(
        @NotBlank String loaiYeuCau,
        @Size(max = 500) String noiDung
) {
}
