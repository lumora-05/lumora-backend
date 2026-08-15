package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DeliveryAddressSuggestionResponse;
import com.example.restaurant.dto.DeliveryOrderCreateRequest;
import com.example.restaurant.dto.DeliveryOrderCreateResponse;
import com.example.restaurant.dto.DeliveryQuoteRequest;
import com.example.restaurant.dto.DeliveryQuoteResponse;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.dto.DeliveryTrackingResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.service.CustomerAccountService;
import com.example.restaurant.service.DeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/delivery")
public class CustomerDeliveryController {
    private final DeliveryOrderService deliveryOrderService;
    private final CustomerAccountService customerAccountService;

    public CustomerDeliveryController(DeliveryOrderService deliveryOrderService,
                                      CustomerAccountService customerAccountService) {
        this.deliveryOrderService = deliveryOrderService;
        this.customerAccountService = customerAccountService;
    }


    /** Gợi ý địa chỉ khi khách đang gõ; frontend phải để khách chọn một kết quả cụ thể. */
    @GetMapping("/address-suggestions")
    public ResponseEntity<ApiResponse<java.util.List<DeliveryAddressSuggestionResponse>>> addressSuggestions(
            @RequestParam String query,
            @RequestParam String tinhThanh,
            @RequestParam(required = false) String phuongXa) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gợi ý địa chỉ thành công",
                deliveryOrderService.suggestAddresses(query, tinhThanh, phuongXa)
        ));
    }

    /** Backend tự xác định khu vực và phí giao; frontend không được tự quyết định phí. */
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<DeliveryQuoteResponse>> quote(
            @Valid @RequestBody DeliveryQuoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tính phí giao hàng thành công",
                deliveryOrderService.quote(request)
        ));
    }

    /** Khách từ xa đặt món; backend tự kiểm tra điều kiện trước khi nhận đơn. */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<DeliveryOrderCreateResponse>> createOrder(
            @Valid @RequestBody DeliveryOrderCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đặt món trực tuyến thành công",
                deliveryOrderService.createOrder(
                        request,
                        customerAccountService.resolveOptionalCustomer(authorization))
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

    /** VietQR được tạo cho đơn đã qua kiểm tra cuối và đang chờ thanh toán. */
    @GetMapping("/orders/{trackingToken}/vietqr")
    public ResponseEntity<ApiResponse<VietQrResponse>> vietQr(
            @PathVariable String trackingToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo VietQR cho đơn giao hàng thành công",
                deliveryOrderService.createVietQr(trackingToken)
        ));
    }

    /** Khách được tự hủy khi đơn chưa được nhà hàng xác nhận; VietQR đã trả tiền sẽ vào hàng chờ hoàn. */
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
