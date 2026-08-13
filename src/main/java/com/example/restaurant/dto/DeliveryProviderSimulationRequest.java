package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryProviderSimulationRequest(
        @NotBlank @Size(max = 40) String trangThai,
        @Size(max = 500) String lyDo
) {
}
