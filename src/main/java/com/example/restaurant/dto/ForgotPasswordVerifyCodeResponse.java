package com.example.restaurant.dto;

public record ForgotPasswordVerifyCodeResponse(
        String resetToken,
        long expiresInSeconds
) {
}
