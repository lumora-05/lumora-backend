package com.example.restaurant.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Cấu trúc phản hồi phân trang ổn định cho frontend.
 * Không trả trực tiếp Page của Spring để tránh phụ thuộc vào cấu trúc JSON nội bộ.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        int numberOfElements,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getNumberOfElements(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast(),
                source.isEmpty()
        );
    }
}
