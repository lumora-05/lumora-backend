package com.example.restaurant.dto.realtime;

import java.time.LocalDateTime;

public record RealtimeEventResponse(
        String type,
        String message,
        Object data,
        LocalDateTime createdAt
) {
    public static RealtimeEventResponse of(String type, String message, Object data) {
        return new RealtimeEventResponse(type, message, data, LocalDateTime.now());
    }
}
