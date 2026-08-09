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
    private String mockProviderName = "GrabExpress (Demo)";
    private String mockWaybillPrefix = "GRAB-DEMO";
    private Integer maxUnitsPerItem = 50;
    private Integer maxUnitsPerOrder = 100;
    private Integer paymentTimeoutMinutes = 15;
    private Integer confirmationWarningMinutes = 10;
    private Integer driverAssignmentProgressPercent = 70;
    private String providerWebhookToken = "";
}
