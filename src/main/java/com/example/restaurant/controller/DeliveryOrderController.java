package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DeliveryHandoverRequest;
import com.example.restaurant.dto.DeliveryPaymentConfirmRequest;
import com.example.restaurant.dto.DeliveryProviderSimulationRequest;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.dto.DeliveryRefundConfirmRequest;
import com.example.restaurant.entity.Order;
import com.example.restaurant.service.DeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
            @RequestParam(required = false) String deliveryStatus,
            Authentication authentication) {
        boolean kitchenOnly = isKitchenOnly(authentication);
        List<Order> orders;
        if (deliveryStatus == null || deliveryStatus.isBlank()) {
            orders = kitchenOnly
                    ? deliveryOrderService.findAllForKitchen()
                    : deliveryOrderService.findAll();
        } else {
            orders = kitchenOnly
                    ? deliveryOrderService.findByDeliveryStatusForKitchen(deliveryStatus)
                    : deliveryOrderService.findByDeliveryStatus(deliveryStatus);
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn giao hàng thành công", orders));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','KITCHEN')")
    public ResponseEntity<ApiResponse<Order>> detail(
            @PathVariable Integer orderId,
            Authentication authentication) {
        Order order = isKitchenOnly(authentication)
                ? deliveryOrderService.findByIdForKitchen(orderId)
                : deliveryOrderService.findById(orderId);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chi tiết đơn giao hàng thành công",
                order
        ));
    }

    /** VietQR được ghi nhận trước; sau đó đơn vẫn chờ nhà hàng xác nhận. */
    @PostMapping("/{orderId}/confirm-vietqr")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirmVietQr(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryPaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận tiền VietQR; đơn đang chờ nhà hàng xác nhận",
                deliveryOrderService.confirmVietQrPayment(orderId, request)
        ));
    }

    /** Xác nhận đơn sau khi kiểm tra thông tin khách, địa chỉ, món và thanh toán. */
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirm(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận đơn giao hàng",
                deliveryOrderService.confirmOrder(orderId)
        ));
    }

    /** Từ chối đơn trước khi bếp bắt đầu chế biến. */
    @PostMapping("/{orderId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> reject(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã từ chối đơn giao hàng",
                deliveryOrderService.rejectOrder(orderId, request)
        ));
    }

    /** Xác nhận khoản hoàn tiền toàn phần hoặc phần chênh lệch sau khi món/đơn bị hủy. */
    @PostMapping("/{orderId}/confirm-refund")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirmRefund(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryRefundConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận hoàn tiền cho khách",
                deliveryOrderService.confirmRefund(orderId, request)
        ));
    }

    /** Chỉ bàn giao khi toàn bộ món hoàn thành và đã có tài xế được điều phối. */
    @PostMapping("/{orderId}/handover")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> handover(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryHandoverRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã bàn giao đơn cho tài xế do đơn vị vận chuyển điều phối",
                deliveryOrderService.handover(orderId, request)
        ));
    }

    /** Bước nội bộ sau giao thành công: ghi nhận hóa đơn/kế toán, không hiển thị trong hành trình khách. */
    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> complete(
            @PathVariable Integer orderId,
            Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận hóa đơn cho đơn giao thành công",
                deliveryOrderService.complete(orderId, username)
        ));
    }

    /** Luồng dự phòng khi cần ghi nhận thủ công sự cố từ đối tác. */
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

    /** Mô phỏng webhook của đối tác vận chuyển phục vụ trình diễn đồ án. */
    @PostMapping("/{orderId}/simulate-provider-result")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> simulateProviderResult(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryProviderSimulationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã mô phỏng trạng thái trả về từ đơn vị vận chuyển",
                deliveryOrderService.simulateProviderResult(orderId, request)
        ));
    }

    @PostMapping("/{orderId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> retry(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã yêu cầu đơn vị vận chuyển điều phối lại tài xế",
                deliveryOrderService.retry(orderId)
        ));
    }

    private boolean isKitchenOnly(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean kitchen = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_KITCHEN".equals(authority.getAuthority()));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return kitchen && !admin;
    }

}
