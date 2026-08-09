package com.example.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cai_dat_he_thong")
public class SystemSetting {
    public static final Integer SINGLETON_ID = 1;

    @Id
    @Column(name = "ma_cai_dat")
    private Integer maCaiDat = SINGLETON_ID;

    // Thông tin nhà hàng
    @Column(name = "ten_nha_hang", nullable = false, length = 120)
    private String tenNhaHang;

    @Column(name = "dia_chi", length = 255)
    private String diaChi;

    @Column(name = "so_dien_thoai", length = 30)
    private String soDienThoai;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "gio_mo_cua", length = 100)
    private String gioMoCua;

    @Column(name = "reservation_url", length = 255)
    private String reservationUrl;

    @Column(name = "menu_url", length = 255)
    private String menuUrl;

    // Thương hiệu & giao diện
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "banner_url", length = 1000)
    private String bannerUrl;

    // Đặt bàn
    @Column(name = "reservation_default_duration_minutes")
    private Integer reservationDefaultDurationMinutes;

    @Column(name = "reservation_preparation_minutes")
    private Integer reservationPreparationMinutes;

    @Column(name = "reservation_no_show_grace_minutes")
    private Integer reservationNoShowGraceMinutes;

    @Column(name = "reservation_check_in_early_minutes")
    private Integer reservationCheckInEarlyMinutes;

    @Column(name = "reservation_minimum_advance_minutes")
    private Integer reservationMinimumAdvanceMinutes;

    @Column(name = "reservation_maximum_advance_days")
    private Integer reservationMaximumAdvanceDays;

    // Thanh toán VietQR
    @Column(name = "vietqr_bank_id", length = 30)
    private String vietQrBankId;

    @Column(name = "vietqr_bank_name", length = 120)
    private String vietQrBankName;

    @Column(name = "vietqr_account_no", length = 50)
    private String vietQrAccountNo;

    @Column(name = "vietqr_account_name", length = 160)
    private String vietQrAccountName;

    @Column(name = "vietqr_template", length = 30)
    private String vietQrTemplate;

    @Column(name = "vietqr_description_prefix", length = 50)
    private String vietQrDescriptionPrefix;

    // Tích điểm
    @Column(name = "loyalty_money_per_earned_point", precision = 18, scale = 2)
    private BigDecimal loyaltyMoneyPerEarnedPoint;

    @Column(name = "loyalty_value_per_redeemed_point", precision = 18, scale = 2)
    private BigDecimal loyaltyValuePerRedeemedPoint;

    @Column(name = "loyalty_minimum_redeem_points")
    private Integer loyaltyMinimumRedeemPoints;

    @Column(name = "loyalty_maximum_redeem_ratio", precision = 6, scale = 4)
    private BigDecimal loyaltyMaximumRedeemRatio;

    // Chatbot AI (không lưu API key trong database)
    @Column(name = "chatbot_enabled")
    private Boolean chatbotEnabled;

    @Column(name = "chatbot_model", length = 120)
    private String chatbotModel;

    @Column(name = "chatbot_timeout_seconds")
    private Integer chatbotTimeoutSeconds;

    @Column(name = "chatbot_max_output_tokens")
    private Integer chatbotMaxOutputTokens;

    @Column(name = "chatbot_max_history_messages")
    private Integer chatbotMaxHistoryMessages;

    @Column(name = "chatbot_minimum_confidence", precision = 5, scale = 4)
    private BigDecimal chatbotMinimumConfidence;

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao;

    @Column(name = "ngay_cap_nhat", nullable = false)
    private LocalDateTime ngayCapNhat;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (maCaiDat == null) {
            maCaiDat = SINGLETON_ID;
        }
        if (ngayTao == null) {
            ngayTao = now;
        }
        ngayCapNhat = now;
    }

    @PreUpdate
    void onUpdate() {
        ngayCapNhat = LocalDateTime.now();
    }
}
