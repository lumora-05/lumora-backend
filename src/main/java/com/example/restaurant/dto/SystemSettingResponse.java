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
    private Integer reservationCheckInEarlyMinutes;
    private Integer reservationMinimumAdvanceMinutes;
    private Integer reservationMaximumAdvanceDays;
    private BigDecimal reservationDepositAmount;
    private Integer reservationDepositPaymentTimeoutMinutes;
    private Integer reservationDepositRefundAdvanceMinutes;

    // Giao hàng theo quãng đường
    private Double deliveryTier1DistanceKm;
    private Double deliveryTier2DistanceKm;
    private Double deliveryMaxDistanceKm;
    private BigDecimal deliveryTier1Fee;
    private BigDecimal deliveryTier2Fee;
    private BigDecimal deliveryTier3Fee;

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
