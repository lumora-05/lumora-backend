package com.example.restaurant.config;

import com.example.restaurant.service.SystemSettingService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Nạp các cài đặt lưu trong database vào các policy runtime ngay khi ứng dụng
 * sẵn sàng, để scheduler và API đặt bàn không phải chờ frontend gọi endpoint
 * cài đặt trước.
 */
@Component
public class SystemSettingRuntimeInitializer {
    private final SystemSettingService systemSettingService;

    public SystemSettingRuntimeInitializer(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadRuntimeSettings() {
        systemSettingService.getSettings();
    }
}
