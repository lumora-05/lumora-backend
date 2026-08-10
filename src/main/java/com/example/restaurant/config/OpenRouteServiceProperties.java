package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.open-route-service")
public class OpenRouteServiceProperties {
    /** Bật/tắt định vị và tính tuyến đường miễn phí bằng openrouteservice + OpenStreetMap. */
    private Boolean enabled = true;
    /** API key Standard 0€ của openrouteservice/HeiGIT, chỉ dùng ở backend. */
    private String apiKey = "";
    private String directionsUrl = "https://api.heigit.org/openrouteservice/v2/directions/driving-car/geojson";
    private String geocodeUrl = "https://api.heigit.org/pelias/v1/search";
    /** Tọa độ cố định của nhà hàng, dùng làm điểm xuất phát tính tuyến đường. */
    private Double originLatitude = 16.075733;
    private Double originLongitude = 108.169949;

    /**
     * Giữ lại địa chỉ gốc để tương thích cấu hình cũ/tham chiếu,
     * nhưng không còn dùng để geocode điểm xuất phát.
     */
    private String originAddress = "139 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng, Việt Nam";
    private String countryCode = "VN";
    private String languageCode = "vi";

    /** Bảng phí giao theo quãng đường đường bộ do openrouteservice trả về. */
    private Double tier1DistanceKm = 3.0;
    private Double tier2DistanceKm = 6.0;
    private Double maxDeliveryDistanceKm = 10.0;
    private BigDecimal tier1Fee = new BigDecimal("15000");
    private BigDecimal tier2Fee = new BigDecimal("20000");
    private BigDecimal tier3Fee = new BigDecimal("30000");
}
