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
}
