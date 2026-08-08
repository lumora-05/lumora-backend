package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.SystemSettingRequest;
import com.example.restaurant.dto.SystemSettingResponse;
import com.example.restaurant.service.SystemSettingService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/system-settings")
public class SystemSettingController {
    private final SystemSettingService systemSettingService;

    public SystemSettingController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> publicSettings() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin nhà hàng thành công",
                systemSettingService.getSettings()
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> adminSettings() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy cài đặt hệ thống thành công",
                systemSettingService.getSettings()
        ));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> update(
            @Valid @RequestBody SystemSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật cài đặt hệ thống thành công",
                systemSettingService.update(request)
        ));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> updateLogo(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật logo nhà hàng thành công",
                systemSettingService.updateLogo(file)
        ));
    }

    @PostMapping(value = "/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> updateBanner(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật banner trang chủ thành công",
                systemSettingService.updateBanner(file)
        ));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> removeLogo() {
        return ResponseEntity.ok(ApiResponse.success(
                "Xóa logo tùy chỉnh thành công",
                systemSettingService.removeLogo()
        ));
    }

    @DeleteMapping("/banner")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemSettingResponse>> removeBanner() {
        return ResponseEntity.ok(ApiResponse.success(
                "Xóa banner tùy chỉnh thành công",
                systemSettingService.removeBanner()
        ));
    }
}
