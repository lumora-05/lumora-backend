package com.example.restaurant.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class SystemSettingResponse {
    private Integer id;

    // Thông tin nhà hàng
    private String restaurantName;
    private String address;
    private String phone;
    private String email;
    private String openingHours;
    private String reservationUrl;
    private String menuUrl;

    // Thương hiệu & giao diện
    private String logoUrl;
    private String bannerUrl;

    // Đặt bàn
    private Integer reservationDefaultDurationMinutes;
    private Integer reservationPreparationMinutes;
    private Integer reservationNoShowGraceMinutes;

    // Thanh toán
    private String vietQrBankId;
    private String vietQrBankName;
    private String vietQrAccountNo;
    private String vietQrAccountName;
    private String vietQrTemplate;
    private String vietQrDescriptionPrefix;

    // Tích điểm
    private BigDecimal loyaltyMoneyPerEarnedPoint;
    private BigDecimal loyaltyValuePerRedeemedPoint;
    private Integer loyaltyMinimumRedeemPoints;
    private BigDecimal loyaltyMaximumRedeemRatio;

    // Chatbot
    private Boolean chatbotEnabled;
    private String chatbotModel;
    private Integer chatbotTimeoutSeconds;
    private Integer chatbotMaxOutputTokens;
    private Integer chatbotMaxHistoryMessages;
    private BigDecimal chatbotMinimumConfidence;

    private LocalDateTime updatedAt;
}
