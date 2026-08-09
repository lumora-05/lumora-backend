package com.example.restaurant.dto;

import java.time.LocalDateTime;

public record DeliveryOtpRequestResponse(
        String requestId,
        LocalDateTime expiresAt,
        String demoOtp
) {
}
