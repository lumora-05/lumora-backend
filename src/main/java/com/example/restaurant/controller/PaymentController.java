package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.LoyaltyPreviewResponse;
import com.example.restaurant.dto.PaymentRequest;
import com.example.restaurant.dto.PaymentSlipResponse;
import com.example.restaurant.dto.PayOsWebhookResponse;
import com.example.restaurant.dto.RevenueResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.entity.Invoice;
import com.example.restaurant.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Xác nhận thanh toán tiền mặt. Chuyển khoản VietQR không đi qua endpoint
     * này nữa mà được hoàn tất tự động khi webhook payOS hợp lệ được nhận.
     */
    @PostMapping
    @PreAuthorize("hasRole('CASHIER')")
    public ResponseEntity<ApiResponse<Invoice>> createInvoice(
            @Valid @RequestBody PaymentRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : null;
        Invoice invoice = paymentService.createInvoice(request, username);
        return ResponseEntity.ok(ApiResponse.success("Thanh toán và tạo hóa đơn thành công", invoice));
    }

    /** Xem trước số điểm có thể dùng, tiền giảm và điểm dự kiến được cộng. */
    @GetMapping("/loyalty-preview/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<LoyaltyPreviewResponse>> loyaltyPreview(
            @PathVariable Integer orderId,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0") Integer pointsToUse) {
        LoyaltyPreviewResponse response = paymentService.previewLoyalty(orderId, phone, pointsToUse);
        return ResponseEntity.ok(ApiResponse.success("Tính thử điểm tích lũy thành công", response));
    }

    /**
     * Tạo payment request payOS và trả VietQR theo đúng số tiền của đơn. Việc
     * mở QR chưa được xem là thanh toán; trạng thái chỉ đổi sau webhook hợp lệ.
     */
    @GetMapping("/vietqr/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<VietQrResponse>> vietQrByOrder(
            @PathVariable Integer orderId,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0") Integer pointsToUse,
            Principal principal) {
        String username = principal != null ? principal.getName() : null;
        VietQrResponse response = paymentService.createPayOsVietQr(orderId, phone, pointsToUse, username);
        return ResponseEntity.ok(ApiResponse.success("Tạo VietQR payOS cho đơn hàng thành công", response));
    }

    /**
     * Webhook công khai cho payOS. Không dùng JWT vì payOS gọi trực tiếp từ máy
     * chủ của họ; độ tin cậy được xác minh bằng HMAC-SHA256 checksum key.
     */
    @PostMapping("/payos/webhook")
    public ResponseEntity<PayOsWebhookResponse> payOsWebhook(@RequestBody JsonNode body) {
        return ResponseEntity.ok(paymentService.handlePayOsWebhook(body));
    }

    /** Đăng ký/cập nhật webhook payOS sau khi backend đã có URL HTTPS công khai. */
    @PostMapping("/payos/register-webhook")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<JsonNode>> registerPayOsWebhook() {
        JsonNode response = paymentService.registerPayOsWebhook();
        return ResponseEntity.ok(ApiResponse.success("Đăng ký webhook payOS thành công", response));
    }

    /**
     * Trả dữ liệu để frontend in phiếu tạm tính có VietQR. Việc mở hoặc in
     * phiếu không được xem là thanh toán thành công.
     */
    @GetMapping("/payment-slip/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<PaymentSlipResponse>> paymentSlipByOrder(
            @PathVariable Integer orderId) {
        PaymentSlipResponse response = paymentService.createPaymentSlip(orderId);
        return ResponseEntity.ok(ApiResponse.success("Tạo dữ liệu phiếu tạm tính thành công", response));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Invoice>> invoiceByOrder(@PathVariable Integer orderId) {
        Invoice invoice = paymentService.findByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success("Lấy hóa đơn theo đơn hàng thành công", invoice));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RevenueResponse>> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RevenueResponse revenue = paymentService.revenue(from, to);
        return ResponseEntity.ok(ApiResponse.success("Lấy báo cáo doanh thu thành công", revenue));
    }
}
