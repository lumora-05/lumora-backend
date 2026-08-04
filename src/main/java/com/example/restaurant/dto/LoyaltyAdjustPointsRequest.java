package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoyaltyAdjustPointsRequest(
        @NotNull(message = "Số điểm điều chỉnh không được để trống")
        Integer soDiem,

        @NotBlank(message = "Lý do điều chỉnh không được để trống")
        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String lyDo
) {
}
