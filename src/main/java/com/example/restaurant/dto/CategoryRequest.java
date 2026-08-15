package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String tenDanhMuc,
        @Size(max = 150) String tenDanhMucEn,
        @Size(max = 255) String moTa,
        @Size(max = 500) String moTaEn,
        Boolean trangThai
) {
}
