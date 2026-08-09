package com.example.restaurant.scheduler;

import com.example.restaurant.service.DeliveryOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryOrderMaintenanceScheduler {
    private final DeliveryOrderService deliveryOrderService;

    public DeliveryOrderMaintenanceScheduler(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    /** Cảnh báo đơn chờ xác nhận lâu và tự hủy VietQR hết hạn. */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 45_000L)
    public void maintainDeliveryOrders() {
        deliveryOrderService.performMaintenance();
    }
}
