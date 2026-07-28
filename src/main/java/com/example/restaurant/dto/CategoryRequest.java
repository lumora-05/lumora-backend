package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String tenDanhMuc,
        String moTa,
        Boolean trangThai
) {
}
