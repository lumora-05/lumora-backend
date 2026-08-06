package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.delivery")
public class DeliveryProperties {
    private BigDecimal innerAreaFee = new BigDecimal("15000");
    private BigDecimal nearbyAreaFee = new BigDecimal("25000");
    private String shippingCodePrefix = "LUM-VC";
    private Integer maxUnitsPerItem = 50;
    private Integer maxUnitsPerOrder = 100;
}
