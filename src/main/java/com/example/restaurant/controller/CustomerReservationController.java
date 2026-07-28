package com.example.restaurant.controller;

import com.example.restaurant.dto.*;
import com.example.restaurant.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/reservations")
public class CustomerReservationController {
    private final ReservationService reservationService;

    public CustomerReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/areas")
    public ResponseEntity<ApiResponse<java.util.List<String>>> areas() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách khu vực đặt bàn thành công",
                reservationService.publicAreas()
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> create(
            @Valid @RequestBody ReservationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gửi yêu cầu đặt bàn thành công",
                reservationService.create(request)
        ));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<ReservationResponse>> detail(
            @PathVariable String code,
            @RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin đặt bàn thành công",
                reservationService.findForCustomer(code, phone)
        ));
    }

    @PutMapping("/{code}")
    public ResponseEntity<ApiResponse<ReservationResponse>> update(
            @PathVariable String code,
            @RequestParam String phone,
            @Valid @RequestBody ReservationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật yêu cầu đặt bàn thành công",
                reservationService.updateByCustomer(code, phone, request)
        ));
    }

    @PostMapping("/{code}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancel(
            @PathVariable String code,
            @RequestParam String phone,
            @Valid @RequestBody ReservationCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Hủy yêu cầu đặt bàn thành công",
                reservationService.cancelByCustomer(code, phone, request)
        ));
    }
}
