package com.example.restaurant.repository;

import com.example.restaurant.entity.ChatMessage;
import com.example.restaurant.entity.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionOrderByCreatedAtDesc(ChatSession session, Pageable pageable);
}
