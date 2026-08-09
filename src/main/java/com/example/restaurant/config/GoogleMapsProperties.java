package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google-maps")
public class GoogleMapsProperties {
    /** Bật/tắt tích hợp Google Maps cho luồng giao hàng. */
    private Boolean enabled = true;
    /** API key chỉ dùng ở backend cho Routes API, không gửi xuống trình duyệt. */
    private String serverApiKey = "";
    private String routesUrl = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private String originAddress = "139 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng, Việt Nam";
    private String regionCode = "vn";
    private String languageCode = "vi";

    /** Bảng phí giao theo quãng đường thực tế Google Routes. */
    private Double tier1DistanceKm = 3.0;
    private Double tier2DistanceKm = 6.0;
    private Double maxDeliveryDistanceKm = 10.0;
    private BigDecimal tier1Fee = new BigDecimal("15000");
    private BigDecimal tier2Fee = new BigDecimal("20000");
    private BigDecimal tier3Fee = new BigDecimal("30000");
}
