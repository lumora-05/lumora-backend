package com.example.restaurant.controller;

import com.example.restaurant.dto.AdminNotificationResponse;
import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.dto.UnreadNotificationCountResponse;
import com.example.restaurant.service.AdminNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {
    private final AdminNotificationService notificationService;

    public AdminNotificationController(AdminNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminNotificationResponse>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        PageResponse<AdminNotificationResponse> result = PageResponse.from(
                notificationService.findPage(page, size, unread, type, keyword)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách thông báo Admin thành công", result));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy số thông báo chưa đọc thành công",
                new UnreadNotificationCountResponse(notificationService.countUnread())
        ));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<AdminNotificationResponse>> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đánh dấu thông báo là đã đọc",
                notificationService.markAsRead(id)
        ));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead() {
        int updated = notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đánh dấu tất cả thông báo là đã đọc",
                Map.of("soLuongDaCapNhat", updated)
        ));
    }

    @PostMapping("/sync-low-stock")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> synchronizeLowStock() {
        int created = notificationService.synchronizeCurrentLowStock();
        return ResponseEntity.ok(ApiResponse.success(
                "Đồng bộ cảnh báo nguyên liệu sắp hết thành công",
                Map.of("soLuongThongBaoMoi", created)
        ));
    }
}
