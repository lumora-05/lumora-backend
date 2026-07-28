package com.example.restaurant.config;

import com.example.restaurant.service.AdminNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryNotificationInitializer {
    private static final Logger log = LoggerFactory.getLogger(InventoryNotificationInitializer.class);

    private final AdminNotificationService notificationService;

    public InventoryNotificationInitializer(AdminNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeExistingLowStock() {
        try {
            int created = notificationService.synchronizeCurrentLowStock();
            if (created > 0) {
                log.info("Đã tạo {} thông báo tồn kho thấp khi khởi động", created);
            }
        } catch (RuntimeException ex) {
            // Đồng bộ cảnh báo là tác vụ bổ trợ, không làm ứng dụng ngừng khởi động.
            log.error("Không thể đồng bộ cảnh báo tồn kho thấp khi khởi động", ex);
        }
    }
}
