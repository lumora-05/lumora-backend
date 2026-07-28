package com.example.restaurant.dto.chatbot;

import java.util.Map;

public record ChatbotActionResponse(
        String label,
        String action,
        String url,
        Map<String, Object> payload
) {
    public static ChatbotActionResponse of(String label, String action) {
        return new ChatbotActionResponse(label, action, null, Map.of());
    }

    public static ChatbotActionResponse link(String label, String action, String url) {
        return new ChatbotActionResponse(label, action, url, Map.of());
    }
}
