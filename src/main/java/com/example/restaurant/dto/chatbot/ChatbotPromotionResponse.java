package com.example.restaurant.dto.chatbot;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChatbotPromotionResponse(
        Integer id,
        String code,
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderValue,
        BigDecimal maximumDiscount,
        LocalDate startDate,
        LocalDate endDate
) {
}
