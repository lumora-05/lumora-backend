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
     * Luồng mới không còn bước thu ngân duyệt đơn. COD hợp lệ được backend
     * chuyển thẳng xuống bếp; VietQR chỉ chờ ghi nhận thanh toán.
     */
    @PostMapping("/{orderId}/confirm-vietqr")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Order>> confirmVietQr(
            @PathVariable Integer orderId,
            @Valid @RequestBody DeliveryPaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận tiền VietQR và chuyển đơn xuống bếp",
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
}
