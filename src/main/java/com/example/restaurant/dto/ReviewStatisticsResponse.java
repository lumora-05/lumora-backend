package com.example.restaurant.dto;

import java.util.Map;

public record ReviewStatisticsResponse(
        long totalReviews,
        double averageRating,
        long visibleReviews,
        long hiddenReviews,
        Map<Integer, Long> ratingDistribution
) {
}
