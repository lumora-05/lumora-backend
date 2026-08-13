package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryReasonRequest(
        @NotBlank @Size(max = 500) String lyDo
) {
}
