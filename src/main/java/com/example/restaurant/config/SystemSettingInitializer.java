package com.example.restaurant.config;

import com.example.restaurant.service.SystemSettingService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemSettingInitializer implements ApplicationRunner {
    private final SystemSettingService systemSettingService;

    public SystemSettingInitializer(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        systemSettingService.initializeRuntimeSettings();
    }
}
