package com.example.restaurant.dto.chatbot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatbotMessageRequest(
        @NotBlank(message = "Nội dung câu hỏi không được để trống")
        @Size(max = 1000, message = "Nội dung câu hỏi không được vượt quá 1000 ký tự")
        String message,

        @Size(max = 64, message = "Mã phiên trò chuyện không hợp lệ")
        String sessionId,

        @Size(max = 255, message = "QR token không hợp lệ")
        String qrToken
) {
}
