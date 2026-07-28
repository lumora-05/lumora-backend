package com.example.restaurant.controller;

import com.example.restaurant.dto.*;
import com.example.restaurant.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@PreAuthorize("hasAnyRole('ADMIN','WAITER')")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReservationResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String area,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách đặt bàn thành công",
                reservationService.findAll(
                        status, from, to, keyword, area, page, size,
                        authentication.getName(), admin
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> detail(
            @PathVariable Integer id,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chi tiết đặt bàn thành công",
                reservationService.findDetail(id, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    @GetMapping("/availability/tables")
    public ResponseEntity<ApiResponse<List<ReservationAvailabilityResponse>>> availableTables(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrival,
            @RequestParam Integer partySize,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Integer durationMinutes,
            @RequestParam(required = false) Integer excludeReservationId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách bàn khả dụng thành công",
                reservationService.availableTables(
                        arrival, partySize, area, durationMinutes, excludeReservationId,
                        authentication.getName(), hasRole(authentication, "ROLE_ADMIN")
                )
        ));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirm(
            @PathVariable Integer id,
            @Valid @RequestBody ReservationConfirmRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Xác nhận đặt bàn thành công",
                reservationService.confirm(id, request, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ReservationResponse>> reject(
            @PathVariable Integer id,
            @Valid @RequestBody ReservationCancelRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Từ chối đặt bàn thành công",
                reservationService.reject(id, request, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<ReservationResponse>> checkIn(
            @PathVariable Integer id,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Check-in khách đặt bàn thành công",
                reservationService.checkIn(id, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    @PostMapping("/{id}/assign-table")
    public ResponseEntity<ApiResponse<ReservationResponse>> assignTable(
            @PathVariable Integer id,
            @Valid @RequestBody ReservationAssignTableRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Xếp bàn cho khách thành công",
                reservationService.assignTable(id, request, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<ApiResponse<ReservationResponse>> noShow(
            @PathVariable Integer id,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đánh dấu khách không đến thành công",
                reservationService.markNoShow(id, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancel(
            @PathVariable Integer id,
            @Valid @RequestBody ReservationCancelRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Hủy đặt bàn thành công",
                reservationService.cancelByStaff(id, request, authentication.getName(), hasRole(authentication, "ROLE_ADMIN"))
        ));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
