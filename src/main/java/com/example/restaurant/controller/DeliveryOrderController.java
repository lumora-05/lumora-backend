package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DeliveryHandoverRequest;
import com.example.restaurant.dto.DeliveryPaymentConfirmRequest;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.entity.Order;
import com.example.restaurant.service.DeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/delivery-orders")
public class DeliveryOrderController {
    private final DeliveryOrderService deliveryOrderService;

    public DeliveryOrderController(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','KITCHEN')")
    public ResponseEntity<ApiResponse<List<Order>>> all(
            @RequestParam(required = false) String deliveryStatus) {
        List<Order> orders = deliveryStatus == null || deliveryStatus.isBlank()
                ? deliveryOrderService.findAll()
                : deliveryOrderService.findByDeliveryStatus(deliveryStatus);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn giao hàng thành công", orders));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','KITCHEN')")
    public ResponseEntity<ApiResponse<Order>> detail(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chi tiết đơn giao hàng thành công",
                deliveryOrderService.findById(orderId)
        ));
    }

    /** Thu ngân xác nhận đơn; riêng VietQR phải xác nhận tiền trước. */
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirm(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận đơn và chuyển xuống bếp",
                deliveryOrderService.confirm(orderId)
        ));
    }

    @PostMapping("/{orderId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> reject(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã từ chối đơn giao hàng",
                deliveryOrderService.reject(orderId, request)
        ));
    }

    @PostMapping("/{orderId}/confirm-vietqr")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirmVietQr(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryPaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận nhận tiền VietQR",
                deliveryOrderService.confirmVietQrPayment(orderId, request)
        ));
    }

    /** Chỉ bàn giao sau khi bếp hoàn thành toàn bộ món và hệ thống đã sinh mã vận chuyển. */
    @PostMapping("/{orderId}/handover")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> handover(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryHandoverRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã bàn giao đơn cho người giao hàng bên ngoài",
                deliveryOrderService.handover(orderId, request)
        ));
    }

    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> complete(
            @PathVariable Integer orderId,
            Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận giao hàng thành công và tạo hóa đơn",
                deliveryOrderService.complete(orderId, username)
        ));
    }

    @PostMapping("/{orderId}/fail")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> fail(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận giao hàng thất bại",
                deliveryOrderService.fail(orderId, request)
        ));
    }

    @PostMapping("/{orderId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> retry(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đơn đã được đưa về trạng thái chờ bàn giao lại",
                deliveryOrderService.retry(orderId)
        ));
    }
}
