package com.example.restaurant.dto;

import com.example.restaurant.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Integer id,
        String displayName,
        Integer rating,
        String comment,
        Boolean visible,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getDisplayName(),
                review.getRating(),
                review.getComment(),
                review.getVisible(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
