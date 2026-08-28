package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Cấu hình nghiệp vụ đặt bàn có thể thay đổi từ trang Cài đặt hệ thống.
 * Giá trị mặc định giữ đúng hành vi cũ của hệ thống.
 */
@Getter
@Setter
@Component
public class ReservationPolicyProperties {
    private int defaultDurationMinutes = 120;
    private int tablePreparationMinutes = 30;
    private int noShowGraceMinutes = 15;
    private int checkInEarlyMinutes = 30;
    private int minimumAdvanceMinutes = 30;
    private int maximumAdvanceDays = 60;
    private java.math.BigDecimal depositAmount = new java.math.BigDecimal("100000");
    private int depositPaymentTimeoutMinutes = 10;
    private int depositRefundAdvanceMinutes = 120;
}
