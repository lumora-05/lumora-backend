package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.delivery")
public class DeliveryProperties {
    private BigDecimal innerAreaFee = new BigDecimal("15000");
    private BigDecimal nearbyAreaFee = new BigDecimal("25000");
    private String supportedCity = "Đà Nẵng";
    private List<String> innerDistricts = new ArrayList<>(List.of("Thanh Khê", "Hải Châu"));
    private List<String> nearbyDistricts = new ArrayList<>(List.of("Sơn Trà", "Ngũ Hành Sơn", "Cẩm Lệ", "Liên Chiểu", "Hòa Vang"));
    private String shippingCodePrefix = "LUM-VC";
    private String mockProviderName = "Đối tác giao hàng (Demo)";
    private String mockWaybillPrefix = "DELIVERY-DEMO";
    private Integer maxUnitsPerItem = 50;
    private Integer maxUnitsPerOrder = 100;
    private Integer paymentTimeoutMinutes = 15;
    /** Hẹn giờ phải cách hiện tại tối thiểu bao nhiêu phút. */
    private Integer scheduledMinAdvanceMinutes = 30;
    /** Chỉ cho phép hẹn giờ trong số ngày này để tránh giữ đơn quá xa. */
    private Integer scheduledMaxAdvanceDays = 7;
    /** Thời gian chuẩn bị món ước tính dùng để tính ETA và thời điểm gọi đối tác vận chuyển. */
    private Integer preparationMinutes = 25;
    /** Gọi tài xế trước thời điểm món dự kiến sẵn sàng bao nhiêu phút. */
    private Integer driverAssignmentLeadMinutes = 8;
    /** Thời gian giao dự phòng khi chưa có Google Routes. */
    private Integer fallbackDeliveryMinutes = 20;
    private String providerWebhookToken = "";
}
