package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.dto.ReviewResponse;
import com.example.restaurant.dto.ReviewStatisticsResponse;
import com.example.restaurant.dto.ReviewVisibilityRequest;
import com.example.restaurant.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PageResponse<ReviewResponse> result = PageResponse.from(
                reviewService.findAdminPage(page, size, keyword, rating, status, from, to)
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công", result));
    }

    @GetMapping("/statistics/summary")
    public ResponseEntity<ApiResponse<ReviewStatisticsResponse>> statistics() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thống kê đánh giá thành công",
                reviewService.adminStatistics()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chi tiết đánh giá thành công",
                reviewService.findById(id)
        ));
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateVisibility(
            @PathVariable Integer id,
            @Valid @RequestBody ReviewVisibilityRequest request) {
        String message = Boolean.TRUE.equals(request.visible())
                ? "Hiển thị đánh giá thành công"
                : "Ẩn đánh giá thành công";
        return ResponseEntity.ok(ApiResponse.success(
                message,
                reviewService.updateVisibility(id, request)
        ));
    }
}
