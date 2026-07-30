package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BatchIncidentRequest(
        @NotBlank @Size(max = 50) String loaiSuCo,
        @NotBlank @Size(max = 30) String mucDo,
        @NotBlank @Size(max = 1000) String lyDo,
        @Size(max = 1000) String ghiChu
) {
}
