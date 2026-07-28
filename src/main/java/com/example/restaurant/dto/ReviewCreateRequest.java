package com.example.restaurant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
        @Size(max = 50, message = "Tên hiển thị không được vượt quá 50 ký tự")
        String displayName,

        @NotNull(message = "Vui lòng chọn số sao")
        @Min(value = 1, message = "Số sao phải từ 1 đến 5")
        @Max(value = 5, message = "Số sao phải từ 1 đến 5")
        Integer rating,

        @Size(max = 500, message = "Nhận xét không được vượt quá 500 ký tự")
        String comment
) {
}
