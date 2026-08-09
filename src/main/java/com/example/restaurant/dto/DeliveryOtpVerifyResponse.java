package com.example.restaurant.dto;

import java.time.LocalDateTime;

public record DeliveryOtpVerifyResponse(
        String verificationToken,
        LocalDateTime expiresAt
) {
}
