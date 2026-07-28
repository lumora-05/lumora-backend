package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.ApplyPromotionRequest;
import com.example.restaurant.dto.PromotionRequest;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.Promotion;
import com.example.restaurant.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {
    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Promotion>>> activePromotions() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách khuyến mãi đang áp dụng thành công", promotionService.findActiveNow()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Promotion>>> allPromotions() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách khuyến mãi thành công", promotionService.findAll()));
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<Promotion>>> pagedPromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status) {
        PageResponse<Promotion> result = PageResponse.from(
                promotionService.findPage(page, size, keyword, status)
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách khuyến mãi phân trang thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Promotion>> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết khuyến mãi thành công", promotionService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Promotion>> create(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Thêm khuyến mãi thành công", promotionService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Promotion>> update(@PathVariable Integer id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật khuyến mãi thành công", promotionService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        promotionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Tắt khuyến mãi thành công"));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','WAITER')")
    public ResponseEntity<ApiResponse<Order>> applyToOrder(@Valid @RequestBody ApplyPromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Áp dụng khuyến mãi cho đơn hàng thành công", promotionService.applyToOrder(request)));
    }

    @DeleteMapping("/orders/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','WAITER')")
    public ResponseEntity<ApiResponse<Order>> removeFromOrder(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gỡ khuyến mãi khỏi đơn hàng thành công",
                promotionService.removeFromOrder(orderId)
        ));
    }
}
