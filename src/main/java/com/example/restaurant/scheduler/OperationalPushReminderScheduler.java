package com.example.restaurant.scheduler;

import com.example.restaurant.config.FirebasePushProperties;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.PushDeviceRegistration;
import com.example.restaurant.repository.OrderItemRepository;
import com.example.restaurant.repository.PushDeviceRegistrationRepository;
import com.example.restaurant.repository.ServiceRequestRepository;
import com.example.restaurant.service.FirebasePushNotificationService;
import com.example.restaurant.service.WaiterAreaAccess;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OperationalPushReminderScheduler {
    private static final List<String> READY_STATUSES = List.of(
            "HOAN_THANH", "DA_HOAN_THANH", "SAN_SANG", "SAN_SANG_PHUC_VU"
    );

    /**
     * Các trạng thái đơn không còn cho phép phục vụ xác nhận món ra bàn.
     * CHO_XAC_NHAN cũng bị loại vì đơn chưa vào luồng phục vụ chính thức.
     */
    private static final List<String> NON_SERVABLE_ORDER_STATUSES = List.of(
            "CHO_XAC_NHAN",
            "DA_PHUC_VU",
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN",
            "CHO_TAI_XE_NHAN",
            "CHO_BAN_GIAO",
            "DANG_GIAO",
            "CHO_DOI_SOAT",
            "GIAO_THAT_BAI",
            "DA_THANH_TOAN",
            "DA_HUY"
    );

    private final FirebasePushProperties properties;
    private final FirebasePushNotificationService pushService;
    private final OrderItemRepository orderItemRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final PushDeviceRegistrationRepository pushDeviceRegistrationRepository;

    public OperationalPushReminderScheduler(FirebasePushProperties properties,
                                            FirebasePushNotificationService pushService,
                                            OrderItemRepository orderItemRepository,
                                            ServiceRequestRepository serviceRequestRepository,
                                            PushDeviceRegistrationRepository pushDeviceRegistrationRepository) {
        this.properties = properties;
        this.pushService = pushService;
        this.orderItemRepository = orderItemRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.pushDeviceRegistrationRepository = pushDeviceRegistrationRepository;
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

        remindReadyItemsToAssignedWaiters();
    }

    /**
     * Mỗi phục vụ chỉ nhận số phần món thuộc đúng khu vực mình được phân công.
     * Đồng thời chỉ tính đơn tại bàn còn hoạt động và cộng SUM(soLuong), không đếm số dòng chi tiết.
     */
    private void remindReadyItemsToAssignedWaiters() {
        Map<Integer, Employee> waitersById = new LinkedHashMap<>();
        for (PushDeviceRegistration registration
                : pushDeviceRegistrationRepository.findActiveByChannelWithEmployee("WAITER")) {
            Employee employee = registration.getEmployee();
            if (!isActiveWaiter(employee) || employee.getMaNhanVien() == null) {
                continue;
            }
            waitersById.putIfAbsent(employee.getMaNhanVien(), employee);
        }

        for (Employee waiter : waitersById.values()) {
            Set<String> assignedAreas = WaiterAreaAccess.assignedAreaKeys(waiter);
            if (assignedAreas.isEmpty()) {
                continue;
            }

            Long readyQuantity = orderItemRepository.sumReadyQuantityForWaiterAreas(
                    READY_STATUSES,
                    NON_SERVABLE_ORDER_STATUSES,
                    assignedAreas
            );
            long count = readyQuantity == null ? 0L : readyQuantity;
            if (count <= 0) {
                continue;
            }

            pushService.sendToEmployeeChannel(
                    waiter.getMaNhanVien(),
                    "WAITER",
                    "Còn món đang chờ phục vụ",
                    count + " phần đã hoàn thành và cần mang ra bàn.",
                    "/waiter/orders",
                    "waiter-ready-reminder",
                    true
            );
        }
    }

    private boolean isActiveWaiter(Employee employee) {
        if (employee == null) {
            return false;
        }
        String employeeStatus = normalize(employee.getTrangThai());
        String role = employee.getVaiTro() == null || employee.getVaiTro().getTenVaiTro() == null
                ? ""
                : normalize(employee.getVaiTro().getTenVaiTro()).replace("ROLE_", "");
        return "DANG_LAM_VIEC".equals(employeeStatus) && "WAITER".equals(role);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
