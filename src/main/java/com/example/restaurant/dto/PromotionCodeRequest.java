package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record PromotionCodeRequest(
        @NotBlank String maCode
) {
}
