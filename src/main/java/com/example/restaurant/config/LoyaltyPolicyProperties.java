package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Chính sách tích điểm chạy ở runtime và được đồng bộ từ Cài đặt hệ thống.
 * Giá trị mặc định tương ứng chính sách cũ trước khi có trang cấu hình.
 */
@Getter
@Setter
@Component
public class LoyaltyPolicyProperties {
    private BigDecimal moneyPerEarnedPoint = new BigDecimal("10000");
    private BigDecimal valuePerRedeemedPoint = new BigDecimal("1000");
    private int minimumRedeemPoints = 20;
    private BigDecimal maximumRedeemRatio = new BigDecimal("0.20");
}
