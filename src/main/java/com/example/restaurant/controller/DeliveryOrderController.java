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

    /**
     * Thu ngân nhận đơn sau khi backend kiểm tra khả năng đáp ứng.
     * COD chuyển bếp ngay; VietQR chuyển sang CHO_THANH_TOAN rồi mới xuống bếp sau khi nhận tiền.
     */
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirm(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận tiếp nhận đơn giao hàng",
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

    /** Xác nhận VietQR sau khi đơn đã được nhà hàng nhận; thành công mới chuyển xuống bếp. */
    @PostMapping("/{orderId}/confirm-vietqr")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirmVietQr(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryPaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận tiền VietQR và chuyển đơn xuống bếp",
                deliveryOrderService.confirmVietQrPayment(orderId, request)
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

    /** Sau webhook giao thành công, thu ngân đối soát và ghi nhận hóa đơn kế toán. */
    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> complete(
            @PathVariable Integer orderId,
            Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đối soát giao hàng thành công và tạo hóa đơn",
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

    /** Mô phỏng webhook của GrabExpress Demo phục vụ trình diễn đồ án. */
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
}
