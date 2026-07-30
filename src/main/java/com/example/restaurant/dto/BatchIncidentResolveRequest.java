package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BatchIncidentResolveRequest(
        @NotBlank @Size(max = 30) String trangThaiSuCo,
        @NotBlank @Size(max = 30) String trangThaiAnToanLo,
        @NotBlank @Size(max = 1000) String ketQuaXuLy
) {
}
