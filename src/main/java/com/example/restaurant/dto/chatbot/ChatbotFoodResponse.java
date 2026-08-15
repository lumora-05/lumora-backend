package com.example.restaurant.dto.chatbot;

import java.math.BigDecimal;

public record ChatbotFoodResponse(
        Integer id,
        String name,
        String nameEn,
        BigDecimal price,
        String description,
        String descriptionEn,
        String imageUrl,
        String category,
        String categoryEn,
        boolean available
) {
}
