package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DeliveryOrderCreateRequest;
import com.example.restaurant.dto.DeliveryOrderCreateResponse;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.dto.DeliveryTrackingResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.service.DeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/delivery")
public class CustomerDeliveryController {
    private final DeliveryOrderService deliveryOrderService;

    public CustomerDeliveryController(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    /** Khách từ xa đặt món giao tận nơi; đơn chờ thu ngân xác nhận trước khi xuống bếp. */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<DeliveryOrderCreateResponse>> createOrder(
            @Valid @RequestBody DeliveryOrderCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đặt món giao tận nơi thành công. Nhà hàng sẽ xác nhận đơn trước khi chế biến",
                deliveryOrderService.createOrder(request)
        ));
    }

    /** Tra cứu bằng token ngẫu nhiên, không công khai entity nhân viên hoặc dữ liệu nội bộ. */
    @GetMapping("/orders/{trackingToken}")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> track(
            @PathVariable String trackingToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tra cứu đơn giao hàng thành công",
                deliveryOrderService.track(trackingToken)
        ));
    }

    /** Tạo VietQR theo tổng món sau giảm giá cộng phí giao hàng. */
    @GetMapping("/orders/{trackingToken}/vietqr")
    public ResponseEntity<ApiResponse<VietQrResponse>> vietQr(
            @PathVariable String trackingToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo VietQR cho đơn giao hàng thành công",
                deliveryOrderService.createVietQr(trackingToken)
        ));
    }

    /** Khách chỉ được tự hủy khi nhà hàng chưa xác nhận đơn. */
    @PostMapping("/orders/{trackingToken}/cancel")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> cancel(
            @PathVariable String trackingToken,
            @Valid @RequestBody DeliveryReasonRequest request) {
        deliveryOrderService.cancelByCustomer(trackingToken, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hủy đơn giao hàng",
                deliveryOrderService.track(trackingToken)
        ));
    }
}
