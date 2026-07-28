package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google credential không được để trống")
        String credential
) {
}
