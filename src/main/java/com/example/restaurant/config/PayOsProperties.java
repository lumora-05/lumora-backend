package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình payOS dùng cho thanh toán chuyển khoản tự động bằng webhook. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.payment.payos")
public class PayOsProperties {
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String baseUrl = "https://api-merchant.payos.vn";
    private String returnUrl;
    private String cancelUrl;
    private String webhookUrl;
    private int expireMinutes = 15;

    public boolean isConfigured() {
        return hasText(clientId) && hasText(apiKey) && hasText(checksumKey)
                && hasText(returnUrl) && hasText(cancelUrl);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
