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
    /** Type-ahead để khách chọn địa chỉ từ danh sách gợi ý, tránh backend tự đoán chuỗi nhập dở. */
    private String autocompleteUrl = "https://api.heigit.org/pelias/v1/autocomplete";
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
