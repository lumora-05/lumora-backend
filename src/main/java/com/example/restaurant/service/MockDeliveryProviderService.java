package com.example.restaurant.service;

import com.example.restaurant.config.DeliveryProperties;
import com.example.restaurant.dto.DeliveryProviderAssignment;
import com.example.restaurant.entity.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Mô phỏng API GrabExpress cho mục đích đồ án/demo.
 * Không gọi API Grab thật và không tạo vai trò SHIPPER trong LUMORA.
 */
@Service
public class MockDeliveryProviderService implements DeliveryProviderService {
    private static final List<MockDriver> DRIVERS = List.of(
            new MockDriver("Nguyễn Minh Khang", "0901001001"),
            new MockDriver("Trần Quốc Nam", "0901001002"),
            new MockDriver("Lê Hoàng Phúc", "0901001003"),
            new MockDriver("Phạm Gia Huy", "0901001004")
    );

    private final DeliveryProperties deliveryProperties;

    public MockDeliveryProviderService(DeliveryProperties deliveryProperties) {
        this.deliveryProperties = deliveryProperties;
    }

    @Override
    public DeliveryProviderAssignment createDelivery(Order order) {
        if (order == null || order.getMaDonHang() == null || order.getGiaoHang() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng chưa đủ thông tin để gửi yêu cầu sang đơn vị vận chuyển"
            );
        }

        String provider = StringUtils.hasText(deliveryProperties.getMockProviderName())
                ? deliveryProperties.getMockProviderName().trim()
                : "GrabExpress (Demo)";
        String prefix = StringUtils.hasText(deliveryProperties.getMockWaybillPrefix())
                ? deliveryProperties.getMockWaybillPrefix().trim().toUpperCase(Locale.ROOT)
                : "GRAB-DEMO";
        prefix = prefix.replaceAll("[^A-Z0-9-]", "");
        if (prefix.isBlank()) {
            prefix = "GRAB-DEMO";
        }

        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);
        String waybill = String.format("%s-%08d-%s", prefix, order.getMaDonHang(), randomPart);

        int driverIndex = Math.floorMod((order.getMaDonHang() + randomPart.hashCode()), DRIVERS.size());
        MockDriver driver = DRIVERS.get(driverIndex);

        return new DeliveryProviderAssignment(
                waybill,
                provider,
                driver.name(),
                driver.phone()
        );
    }

    private record MockDriver(String name, String phone) {
    }
}
