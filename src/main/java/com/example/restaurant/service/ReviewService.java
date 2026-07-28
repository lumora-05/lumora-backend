package com.example.restaurant.service;

import com.example.restaurant.dto.ReviewCreateRequest;
import com.example.restaurant.dto.ReviewResponse;
import com.example.restaurant.dto.ReviewStatisticsResponse;
import com.example.restaurant.dto.ReviewVisibilityRequest;
import com.example.restaurant.entity.Review;
import com.example.restaurant.repository.ReviewRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReviewService {
    private static final int MAX_PUBLIC_PAGE_SIZE = 50;
    private static final int MAX_ADMIN_PAGE_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final RealtimeNotificationService realtimeNotificationService;

    public ReviewService(ReviewRepository reviewRepository,
                         RealtimeNotificationService realtimeNotificationService) {
        this.reviewRepository = reviewRepository;
        this.realtimeNotificationService = realtimeNotificationService;
    }

    @Transactional
    public ReviewResponse create(ReviewCreateRequest request) {
        Review review = new Review();
        review.setDisplayName(normalizeDisplayName(request.displayName()));
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        review.setVisible(true);

        Review saved = reviewRepository.save(review);
        ReviewResponse response = ReviewResponse.from(saved);
        realtimeNotificationService.notifyReviewChanged(
                "REVIEW_CREATED",
                "Có đánh giá mới",
                Map.of("id", saved.getId(), "visible", true)
        );
        return response;
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> findPublicPage(int page, int size) {
        Pageable pageable = pageRequest(page, size, MAX_PUBLIC_PAGE_SIZE);
        return reviewRepository.findAll(
                        (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("visible")),
                        pageable
                )
                .map(ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> findAdminPage(int page,
                                              int size,
                                              String keyword,
                                              Integer rating,
                                              String status,
                                              LocalDate from,
                                              LocalDate to) {
        validateFilters(rating, status, from, to);
        Pageable pageable = pageRequest(page, size, MAX_ADMIN_PAGE_SIZE);
        Specification<Review> specification = buildAdminSpecification(keyword, rating, status, from, to);
        return reviewRepository.findAll(specification, pageable).map(ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public ReviewResponse findById(Integer id) {
        return ReviewResponse.from(requireById(id));
    }

    @Transactional
    public ReviewResponse updateVisibility(Integer id, ReviewVisibilityRequest request) {
        Review review = requireById(id);
        review.setVisible(request.visible());
        Review saved = reviewRepository.save(review);
        ReviewResponse response = ReviewResponse.from(saved);

        realtimeNotificationService.notifyReviewChanged(
                "REVIEW_VISIBILITY_CHANGED",
                Boolean.TRUE.equals(saved.getVisible()) ? "Đánh giá đã được hiển thị" : "Đánh giá đã được ẩn",
                Map.of("id", saved.getId(), "visible", Boolean.TRUE.equals(saved.getVisible()))
        );
        return response;
    }

    @Transactional(readOnly = true)
    public ReviewStatisticsResponse publicStatistics() {
        long visible = reviewRepository.countByVisibleTrue();
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 5; rating >= 1; rating--) {
            distribution.put(rating, reviewRepository.countByVisibleTrueAndRating(rating));
        }
        return new ReviewStatisticsResponse(
                visible,
                safeAverage(reviewRepository.findVisibleAverageRating()),
                visible,
                0,
                distribution
        );
    }

    @Transactional(readOnly = true)
    public ReviewStatisticsResponse adminStatistics() {
        long total = reviewRepository.count();
        long visible = reviewRepository.countByVisibleTrue();
        long hidden = reviewRepository.countByVisibleFalse();
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 5; rating >= 1; rating--) {
            distribution.put(rating, reviewRepository.countByRating(rating));
        }
        return new ReviewStatisticsResponse(
                total,
                safeAverage(reviewRepository.findAverageRating()),
                visible,
                hidden,
                distribution
        );
    }

    private Review requireById(Integer id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đánh giá: " + id
                ));
    }

    private Pageable pageRequest(int page, int size, int maxSize) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), maxSize);
        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
        return PageRequest.of(safePage, safeSize, sort);
    }

    private Specification<Review> buildAdminSpecification(String keyword,
                                                           Integer rating,
                                                           String status,
                                                           LocalDate from,
                                                           LocalDate to) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStatus = normalizeStatus(status);

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!normalizedKeyword.isBlank()) {
                String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                Predicate displayNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("displayName"), "")),
                        pattern
                );
                Predicate commentPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("comment"), "")),
                        pattern
                );

                Integer reviewId = parsePositiveInteger(normalizedKeyword);
                if (reviewId != null) {
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.equal(root.get("id"), reviewId),
                            displayNamePredicate,
                            commentPredicate
                    ));
                } else {
                    predicates.add(criteriaBuilder.or(displayNamePredicate, commentPredicate));
                }
            }

            if (rating != null) {
                predicates.add(criteriaBuilder.equal(root.get("rating"), rating));
            }

            if ("VISIBLE".equals(normalizedStatus)) {
                predicates.add(criteriaBuilder.isTrue(root.get("visible")));
            } else if ("HIDDEN".equals(normalizedStatus)) {
                predicates.add(criteriaBuilder.isFalse(root.get("visible")));
            }

            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        from.atStartOfDay()
                ));
            }
            if (to != null) {
                LocalDateTime nextDay = to.plusDays(1).atStartOfDay();
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), nextDay));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateFilters(Integer rating, String status, LocalDate from, LocalDate to) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số sao phải từ 1 đến 5");
        }
        normalizeStatus(status);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank()
                ? "ALL"
                : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "VISIBLE", "HIDDEN").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đánh giá không hợp lệ");
        }
        return value;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String value = displayName.trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String value = comment.trim();
        return value.isEmpty() ? null : value;
    }

    private Integer parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value.startsWith("#") ? value.substring(1) : value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double safeAverage(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? 0.0 : value;
    }
}
