package com.example.restaurant.dto.chatbot;

import java.math.BigDecimal;

public record ChatbotFoodResponse(
        Integer id,
        String name,
        BigDecimal price,
        String description,
        String imageUrl,
        String category,
        boolean available
) {
}
