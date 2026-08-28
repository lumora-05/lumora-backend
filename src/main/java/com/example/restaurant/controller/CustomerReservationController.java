package com.example.restaurant.controller;

import com.example.restaurant.dto.*;
import com.example.restaurant.service.ReservationPreorderService;
import com.example.restaurant.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/reservations")
public class CustomerReservationController {
    private final ReservationService reservationService;
    private final ReservationPreorderService reservationPreorderService;

    public CustomerReservationController(ReservationService reservationService,
                                         ReservationPreorderService reservationPreorderService) {
        this.reservationService = reservationService;
        this.reservationPreorderService = reservationPreorderService;
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

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<java.util.List<ReservationResponse>>> lookup(
            @RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tra cứu đặt bàn thành công",
                reservationService.lookupForCustomer(query)
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

    @GetMapping("/{code}/deposit/vietqr")
    public ResponseEntity<ApiResponse<ReservationDepositVietQrResponse>> depositVietQr(
            @PathVariable String code,
            @RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo VietQR cọc đặt bàn thành công",
                reservationService.createDepositVietQr(code, phone)
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
    @GetMapping("/{code}/preorder")
    public ResponseEntity<ApiResponse<ReservationPreorderResponse>> preorderDetail(
            @PathVariable String code,
            @RequestParam String phone) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thực đơn đặt trước thành công",
                reservationPreorderService.findForCustomer(code, phone)
        ));
    }

    @PutMapping("/{code}/preorder")
    public ResponseEntity<ApiResponse<ReservationPreorderResponse>> savePreorder(
            @PathVariable String code,
            @RequestParam String phone,
            @Valid @RequestBody ReservationPreorderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gửi thực đơn đặt trước thành công",
                reservationPreorderService.saveByCustomer(code, phone, request)
        ));
    }

    @PostMapping("/{code}/preorder/cancel")
    public ResponseEntity<ApiResponse<ReservationPreorderResponse>> cancelPreorder(
            @PathVariable String code,
            @RequestParam String phone,
            @Valid @RequestBody ReservationCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Hủy thực đơn đặt trước thành công",
                reservationPreorderService.cancelByCustomer(code, phone, request)
        ));
    }

}
