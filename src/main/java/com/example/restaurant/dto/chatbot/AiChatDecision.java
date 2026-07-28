package com.example.restaurant.dto.chatbot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Kết quả có cấu trúc do mô hình AI trả về. Backend chỉ dùng kết quả này để
 * hiểu ý định và trích xuất ràng buộc; dữ liệu món, giá, ưu đãi và đơn hàng
 * luôn được truy vấn và kiểm tra lại trong hệ thống LUMORA.
 */
public record AiChatDecision(
        String intent,
        int guestCount,
        BigDecimal budget,
        String budgetScope,
        String priceOrder,
        int resultLimit,
        List<String> foodKeywords,
        List<String> preferences,
        List<String> exclusions,
        boolean clarificationNeeded,
        String clarificationQuestion,
        String assistantMessage,
        String suggestedAction,
        double confidence,
        boolean safetyConcern
) {
    public AiChatDecision {
        intent = intent == null ? "UNKNOWN" : intent.trim();
        budget = budget == null ? BigDecimal.ZERO : budget.max(BigDecimal.ZERO);
        budgetScope = budgetScope == null ? "UNKNOWN" : budgetScope.trim();
        priceOrder = priceOrder == null ? "NONE" : priceOrder.trim().toUpperCase(Locale.ROOT);
        resultLimit = Math.max(0, Math.min(10, resultLimit));
        foodKeywords = foodKeywords == null ? List.of() : List.copyOf(foodKeywords);
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        clarificationQuestion = clarificationQuestion == null ? "" : clarificationQuestion.trim();
        assistantMessage = assistantMessage == null ? "" : assistantMessage.trim();
        suggestedAction = suggestedAction == null ? "NONE" : suggestedAction.trim();
        confidence = Math.max(0d, Math.min(1d, confidence));
    }

    public boolean hasBudget() {
        return budget.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasGuestCount() {
        return guestCount > 0;
    }
}
