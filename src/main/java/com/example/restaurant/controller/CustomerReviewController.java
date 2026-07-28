package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.dto.ReviewCreateRequest;
import com.example.restaurant.dto.ReviewResponse;
import com.example.restaurant.dto.ReviewStatisticsResponse;
import com.example.restaurant.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/reviews")
public class CustomerReviewController {
    private final ReviewService reviewService;

    public CustomerReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cảm ơn bạn đã gửi đánh giá", reviewService.create(request)));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> publicPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        PageResponse<ReviewResponse> result = PageResponse.from(reviewService.findPublicPage(page, size));
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công", result));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ReviewStatisticsResponse>> statistics() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thống kê đánh giá thành công",
                reviewService.publicStatistics()
        ));
    }
}
