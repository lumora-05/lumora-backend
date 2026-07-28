package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.ServiceRequestCancelRequest;
import com.example.restaurant.dto.ServiceRequestCreateRequest;
import com.example.restaurant.dto.ServiceRequestResponse;
import com.example.restaurant.service.ServiceRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/qr/{qrToken}/service-requests")
public class CustomerServiceRequestController {
    private final ServiceRequestService serviceRequestService;

    public CustomerServiceRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> create(
            @PathVariable String qrToken,
            @Valid @RequestBody ServiceRequestCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Yêu cầu đã được gửi đến nhân viên phục vụ",
                serviceRequestService.createByCustomer(qrToken, request)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceRequestResponse>>> recent(
            @PathVariable String qrToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử yêu cầu phục vụ của bàn thành công",
                serviceRequestService.findRecentByCustomerQr(qrToken)
        ));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ServiceRequestResponse>>> active(
            @PathVariable String qrToken) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy yêu cầu đang xử lý của bàn thành công",
                serviceRequestService.findActiveByCustomerQr(qrToken)
        ));
    }

    @PutMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> cancel(
            @PathVariable String qrToken,
            @PathVariable Integer requestId,
            @Valid @RequestBody(required = false) ServiceRequestCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hủy yêu cầu phục vụ",
                serviceRequestService.cancelByCustomer(qrToken, requestId, request)
        ));
    }
}
