package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.ServiceRequestCancelRequest;
import com.example.restaurant.dto.ServiceRequestResponse;
import com.example.restaurant.service.ServiceRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-requests")
public class ServiceRequestController {
    private final ServiceRequestService serviceRequestService;

    public ServiceRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    /** Admin xem toàn bộ; phục vụ chỉ nhận dữ liệu thuộc khu vực được phân công. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<List<ServiceRequestResponse>>> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách yêu cầu phục vụ thành công",
                serviceRequestService.findForActor(
                        status,
                        authentication.getName(),
                        hasRole(authentication, "ROLE_ADMIN")
                )
        ));
    }

    @PutMapping("/{requestId}/accept")
    @PreAuthorize("hasRole('WAITER')")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> accept(
            @PathVariable Integer requestId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tiếp nhận yêu cầu phục vụ",
                serviceRequestService.accept(requestId, authentication.getName())
        ));
    }

    @PutMapping("/{requestId}/complete")
    @PreAuthorize("hasRole('WAITER')")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> complete(
            @PathVariable Integer requestId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hoàn thành yêu cầu phục vụ",
                serviceRequestService.complete(requestId, authentication.getName())
        ));
    }

    @PutMapping("/{requestId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> cancel(
            @PathVariable Integer requestId,
            @Valid @RequestBody(required = false) ServiceRequestCancelRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hủy yêu cầu phục vụ",
                serviceRequestService.cancelByAdmin(requestId, request, authentication.getName())
        ));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
