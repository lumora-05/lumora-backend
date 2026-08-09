package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DeliveryOrderCreateRequest;
import com.example.restaurant.dto.DeliveryOrderCreateResponse;
import com.example.restaurant.dto.DeliveryOtpRequest;
import com.example.restaurant.dto.DeliveryOtpRequestResponse;
import com.example.restaurant.dto.DeliveryOtpVerifyRequest;
import com.example.restaurant.dto.DeliveryOtpVerifyResponse;
import com.example.restaurant.dto.DeliveryQuoteRequest;
import com.example.restaurant.dto.DeliveryQuoteResponse;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.dto.DeliveryTrackingResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.service.DeliveryOrderService;
import com.example.restaurant.service.DeliveryPhoneVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/delivery")
public class CustomerDeliveryController {
    private final DeliveryOrderService deliveryOrderService;
    private final DeliveryPhoneVerificationService phoneVerificationService;

    public CustomerDeliveryController(DeliveryOrderService deliveryOrderService,
                                      DeliveryPhoneVerificationService phoneVerificationService) {
        this.deliveryOrderService = deliveryOrderService;
        this.phoneVerificationService = phoneVerificationService;
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

    /** OTP demo để xác thực số điện thoại trước khi cho phép tạo đơn giao hàng công khai. */
    @PostMapping("/phone-otp/request")
    public ResponseEntity<ApiResponse<DeliveryOtpRequestResponse>> requestOtp(
            @Valid @RequestBody DeliveryOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tạo mã xác thực số điện thoại",
                phoneVerificationService.requestOtp(request.soDienThoai())
        ));
    }

    @PostMapping("/phone-otp/verify")
    public ResponseEntity<ApiResponse<DeliveryOtpVerifyResponse>> verifyOtp(
            @Valid @RequestBody DeliveryOtpVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Xác thực số điện thoại thành công",
                phoneVerificationService.verifyOtp(request.requestId(), request.soDienThoai(), request.otp())
        ));
    }

    /** Khách từ xa đặt món; nhà hàng xác nhận khả năng phục vụ trước. */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<DeliveryOrderCreateResponse>> createOrder(
            @Valid @RequestBody DeliveryOrderCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đặt món giao tận nơi thành công. Nhà hàng sẽ kiểm tra và xác nhận đơn",
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

    /** VietQR chỉ được tạo sau khi nhà hàng đã nhận đơn và chuyển đơn sang chờ thanh toán. */
    @GetMapping("/orders/{trackingToken}/vietqr")
    public ResponseEntity<ApiResponse<VietQrResponse>> vietQr(
            @PathVariable String trackingToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo VietQR cho đơn giao hàng thành công",
                deliveryOrderService.createVietQr(trackingToken)
        ));
    }

    /** Khách có thể hủy trước khi bếp tiếp nhận, gồm cả giai đoạn chờ thanh toán VietQR. */
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
