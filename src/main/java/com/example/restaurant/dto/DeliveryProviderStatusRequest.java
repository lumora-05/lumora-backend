package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryProviderStatusRequest(
        @NotBlank @Size(max = 80) String maVanDon,
        @NotBlank @Size(max = 40) String trangThai,
        @Size(max = 500) String lyDo,
        @Size(max = 120) String eventId
) {
}
