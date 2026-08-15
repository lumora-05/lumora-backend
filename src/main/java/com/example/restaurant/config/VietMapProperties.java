package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vietmap")
public class VietMapProperties {
    /** Bật/tắt VietMap cho gợi ý địa chỉ, lấy tọa độ và tính tuyến giao hàng. */
    private Boolean enabled = true;
    /** Service API key của consumer "default" trên VietMap Console. Chỉ dùng ở backend. */
    private String apiKey = "";

    private String autocompleteUrl = "https://maps.vietmap.vn/api/autocomplete/v4";
    private String placeUrl = "https://maps.vietmap.vn/api/place/v4";
    private String searchUrl = "https://maps.vietmap.vn/api/search/v4";
    private String routeUrl = "https://maps.vietmap.vn/api/route/v4";
    private Integer displayType = 5;

    /** Vị trí nhà hàng Lumora: 191 Hoàng Diệu, Phường Hải Châu, Đà Nẵng. */
    private Double originLatitude = 16.0586243d;
    private Double originLongitude = 108.2169603d;

    /** Bảng phí giao theo quãng đường đường bộ do VietMap Route trả về. */
    private Double tier1DistanceKm = 3.0;
    private Double tier2DistanceKm = 6.0;
    private Double maxDeliveryDistanceKm = 10.0;
    private BigDecimal tier1Fee = new BigDecimal("15000");
    private BigDecimal tier2Fee = new BigDecimal("20000");
    private BigDecimal tier3Fee = new BigDecimal("30000");
}
