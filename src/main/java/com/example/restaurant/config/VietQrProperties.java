package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình tài khoản nhận tiền dùng để tạo VietQR động theo từng đơn hàng. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.payment.vietqr")
public class VietQrProperties {
    private String bankId;
    private String bankName;
    private String accountNo;
    private String accountName;
    private String template = "compact2";
    private String descriptionPrefix = "LUMORA";
}
