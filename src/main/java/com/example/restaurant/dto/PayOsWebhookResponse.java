package com.example.restaurant.dto;

/** Phản hồi ngắn cho payOS; HTTP 2xx xác nhận webhook đã được tiếp nhận. */
public record PayOsWebhookResponse(
        boolean success,
        String message
) {
}
