package com.example.restaurant.dto;

public record ForgotPasswordSendCodeResponse(
        long expiresInSeconds,
        long resendAfterSeconds
) {
}
