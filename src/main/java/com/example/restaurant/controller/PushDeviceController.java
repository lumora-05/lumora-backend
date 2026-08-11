package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.push.PushDeviceRegistrationRequest;
import com.example.restaurant.service.PushDeviceRegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push-devices")
@PreAuthorize("hasAnyRole('ADMIN','WAITER','KITCHEN')")
public class PushDeviceController {
    private final PushDeviceRegistrationService service;

    public PushDeviceController(PushDeviceRegistrationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Void> register(Authentication authentication,
                                      @Valid @RequestBody PushDeviceRegistrationRequest request) {
        service.register(authentication.getName(), request);
        return ApiResponse.success("Đã đăng ký thiết bị nhận thông báo", null);
    }

    @DeleteMapping
    public ApiResponse<Void> unregister(Authentication authentication,
                                        @RequestParam String installationId,
                                        @RequestParam String channel) {
        service.unregister(authentication.getName(), installationId, channel);
        return ApiResponse.success("Đã hủy đăng ký thông báo trên thiết bị", null);
    }
}
