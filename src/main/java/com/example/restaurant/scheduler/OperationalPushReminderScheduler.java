package com.example.restaurant.scheduler;

import com.example.restaurant.config.FirebasePushProperties;
import com.example.restaurant.repository.OrderItemRepository;
import com.example.restaurant.repository.ServiceRequestRepository;
import com.example.restaurant.service.FirebasePushNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OperationalPushReminderScheduler {
    private static final List<String> READY_STATUSES = List.of(
            "HOAN_THANH", "DA_HOAN_THANH", "SAN_SANG", "SAN_SANG_PHUC_VU"
    );

    private final FirebasePushProperties properties;
    private final FirebasePushNotificationService pushService;
    private final OrderItemRepository orderItemRepository;
    private final ServiceRequestRepository serviceRequestRepository;

    public OperationalPushReminderScheduler(FirebasePushProperties properties,
                                            FirebasePushNotificationService pushService,
                                            OrderItemRepository orderItemRepository,
                                            ServiceRequestRepository serviceRequestRepository) {
        this.properties = properties;
        this.pushService = pushService;
        this.orderItemRepository = orderItemRepository;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    @Scheduled(
            fixedDelayString = "${app.firebase.push.reminder-interval-ms:60000}",
            initialDelayString = "${app.firebase.push.reminder-interval-ms:60000}"
    )
    public void remindPendingOperationalWork() {
        if (!Boolean.TRUE.equals(properties.getEnabled())
                || !Boolean.TRUE.equals(properties.getRemindersEnabled())
                || !pushService.isEnabled()) {
            return;
        }

        long waitingKitchen = orderItemRepository
                .countByTrangThaiMonAndSoLuongGreaterThanAndTrangThaiHuyIsNull("CHO_BEP", 0);
        if (waitingKitchen > 0) {
            pushService.sendToChannel(
                    "KITCHEN",
                    "Bếp còn món chưa tiếp nhận",
                    waitingKitchen + " phần vẫn đang chờ bắt đầu chế biến.",
                    "/kitchen/orders",
                    "kitchen-pending-reminder",
                    true
            );
        }

        long newRequests = serviceRequestRepository.countByTrangThai("MOI");
        if (newRequests > 0) {
            pushService.sendToChannel(
                    "WAITER",
                    "Có yêu cầu tại bàn chưa tiếp nhận",
                    newRequests + " yêu cầu vẫn đang chờ nhân viên phục vụ.",
                    "/waiter/requests",
                    "waiter-request-reminder",
                    true
            );
        }

        long readyItems = orderItemRepository
                .countByTrangThaiMonInAndSoLuongGreaterThanAndTrangThaiHuyIsNull(READY_STATUSES, 0);
        if (readyItems > 0) {
            pushService.sendToChannel(
                    "WAITER",
                    "Còn món đang chờ phục vụ",
                    readyItems + " phần đã hoàn thành và cần mang ra bàn.",
                    "/waiter/orders",
                    "waiter-ready-reminder",
                    true
            );
        }
    }
}
