package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.chatbot.ChatbotMessageRequest;
import com.example.restaurant.dto.chatbot.ChatbotResponse;
import com.example.restaurant.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {
    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ChatbotResponse>> sendMessage(
            @Valid @RequestBody ChatbotMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Chatbot đã xử lý câu hỏi thành công",
                chatbotService.reply(request)
        ));
    }

    @GetMapping("/quick-replies")
    public ResponseEntity<ApiResponse<List<String>>> quickReplies() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách câu hỏi nhanh thành công",
                chatbotService.quickReplies()
        ));
    }
}
