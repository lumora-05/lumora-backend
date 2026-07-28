package com.example.restaurant.dto.chatbot;

import java.util.List;

public record ChatbotResponse(
        String sessionId,
        String intent,
        String message,
        List<ChatbotFoodResponse> foods,
        List<ChatbotPromotionResponse> promotions,
        ChatbotOrderResponse order,
        List<ChatbotActionResponse> actions,
        List<String> quickReplies,
        String disclaimer
) {
    public ChatbotResponse {
        foods = foods == null ? List.of() : List.copyOf(foods);
        promotions = promotions == null ? List.of() : List.copyOf(promotions);
        actions = actions == null ? List.of() : List.copyOf(actions);
        quickReplies = quickReplies == null ? List.of() : List.copyOf(quickReplies);
    }
}
