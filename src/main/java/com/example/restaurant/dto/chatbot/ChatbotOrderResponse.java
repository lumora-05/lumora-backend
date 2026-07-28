package com.example.restaurant.dto.chatbot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChatbotOrderResponse(
        Integer orderId,
        String status,
        String statusLabel,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        LocalDateTime updatedAt
) {
}
