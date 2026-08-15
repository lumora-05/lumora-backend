package com.example.restaurant.controller;

import com.example.restaurant.dto.*;
import com.example.restaurant.service.LoyaltyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {
    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LoyaltyPolicyResponse>> policy() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chính sách tích điểm thành công",
                loyaltyService.policy()
        ));
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<PageResponse<LoyaltyCustomerResponse>>> customers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách khách hàng thân thiết thành công",
                PageResponse.from(loyaltyService.findPage(page, size, keyword))
        ));
    }

    @GetMapping("/customers/by-phone")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LoyaltyCustomerResponse>> customerByPhone(
            @RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tìm khách hàng theo số điện thoại thành công",
                loyaltyService.findByPhone(phone)
        ));
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LoyaltyCustomerResponse>> customerById(
            @PathVariable Integer customerId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin khách hàng thành công",
                loyaltyService.findById(customerId)
        ));
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LoyaltyCustomerResponse>> createCustomer(
            @Valid @RequestBody LoyaltyCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo khách hàng thân thiết thành công",
                loyaltyService.create(request)
        ));
    }

    @PutMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LoyaltyCustomerResponse>> updateCustomer(
            @PathVariable Integer customerId,
            @Valid @RequestBody LoyaltyCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật khách hàng thành công",
                loyaltyService.update(customerId, request)
        ));
    }


    @GetMapping("/customers/{customerId}/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<AdminCustomerOrderResponse>>> customerOrders(
            @PathVariable Integer customerId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử đơn hàng của khách hàng thành công",
                loyaltyService.orderHistory(customerId)
        ));
    }

    @PatchMapping("/customers/{customerId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoyaltyCustomerResponse>> updateCustomerStatus(
            @PathVariable Integer customerId,
            @Valid @RequestBody AdminCustomerStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật trạng thái tài khoản khách hàng thành công",
                loyaltyService.updateAccountStatus(customerId, request)
        ));
    }

    @PostMapping("/customers/{customerId}/adjust-points")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoyaltyCustomerResponse>> adjustPoints(
            @PathVariable Integer customerId,
            @Valid @RequestBody LoyaltyAdjustPointsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Điều chỉnh điểm khách hàng thành công",
                loyaltyService.adjustPoints(customerId, request)
        ));
    }

    @GetMapping("/customers/{customerId}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<PageResponse<LoyaltyTransactionResponse>>> transactions(
            @PathVariable Integer customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử điểm khách hàng thành công",
                PageResponse.from(loyaltyService.transactionPage(customerId, page, size))
        ));
    }
}
