package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SystemSettingRequest {
    // Thông tin nhà hàng
    @NotBlank(message = "Tên nhà hàng không được bỏ trống")
    @Size(max = 120, message = "Tên nhà hàng không được vượt quá 120 ký tự")
    private String restaurantName;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
    private String phone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 120, message = "Email không được vượt quá 120 ký tự")
    private String email;

    @Size(max = 100, message = "Giờ mở cửa không được vượt quá 100 ký tự")
    private String openingHours;

    @Size(max = 255, message = "Đường dẫn đặt bàn không được vượt quá 255 ký tự")
    private String reservationUrl;

    @Size(max = 255, message = "Đường dẫn thực đơn không được vượt quá 255 ký tự")
    private String menuUrl;

    // Đặt bàn. Nullable để frontend cũ vẫn cập nhật được các trường thông tin chung.
    @Min(value = 30, message = "Thời lượng đặt bàn mặc định tối thiểu là 30 phút")
    @Max(value = 360, message = "Thời lượng đặt bàn mặc định tối đa là 360 phút")
    private Integer reservationDefaultDurationMinutes;

    @Min(value = 0, message = "Thời gian chuẩn bị bàn không được âm")
    @Max(value = 180, message = "Thời gian chuẩn bị bàn không được vượt quá 180 phút")
    private Integer reservationPreparationMinutes;

    @Min(value = 0, message = "Thời gian chờ khách trễ không được âm")
    @Max(value = 180, message = "Thời gian chờ khách trễ không được vượt quá 180 phút")
    private Integer reservationNoShowGraceMinutes;

    @Min(value = 0, message = "Thời gian cho phép check-in sớm không được âm")
    @Max(value = 180, message = "Thời gian cho phép check-in sớm không được vượt quá 180 phút")
    private Integer reservationCheckInEarlyMinutes;

    @Min(value = 0, message = "Thời gian đặt trước tối thiểu không được âm")
    @Max(value = 1440, message = "Thời gian đặt trước tối thiểu không được vượt quá 1440 phút")
    private Integer reservationMinimumAdvanceMinutes;

    @Min(value = 1, message = "Số ngày đặt trước tối đa phải từ 1 ngày")
    @Max(value = 365, message = "Số ngày đặt trước tối đa không được vượt quá 365 ngày")
    private Integer reservationMaximumAdvanceDays;

    // Giao hàng theo quãng đường
    @DecimalMin(value = "0.1", message = "Mốc khoảng cách mức 1 phải lớn hơn 0 km")
    @DecimalMax(value = "100.0", message = "Mốc khoảng cách mức 1 không được vượt quá 100 km")
    private Double deliveryTier1DistanceKm;

    @DecimalMin(value = "0.1", message = "Mốc khoảng cách mức 2 phải lớn hơn 0 km")
    @DecimalMax(value = "100.0", message = "Mốc khoảng cách mức 2 không được vượt quá 100 km")
    private Double deliveryTier2DistanceKm;

    @DecimalMin(value = "0.1", message = "Khoảng cách giao tối đa phải lớn hơn 0 km")
    @DecimalMax(value = "100.0", message = "Khoảng cách giao tối đa không được vượt quá 100 km")
    private Double deliveryMaxDistanceKm;

    @DecimalMin(value = "0.00", message = "Phí giao mức 1 không được âm")
    private BigDecimal deliveryTier1Fee;

    @DecimalMin(value = "0.00", message = "Phí giao mức 2 không được âm")
    private BigDecimal deliveryTier2Fee;

    @DecimalMin(value = "0.00", message = "Phí giao mức 3 không được âm")
    private BigDecimal deliveryTier3Fee;

    // Thanh toán VietQR
    @Size(max = 30, message = "Mã ngân hàng không được vượt quá 30 ký tự")
    private String vietQrBankId;

    @Size(max = 120, message = "Tên ngân hàng không được vượt quá 120 ký tự")
    private String vietQrBankName;

    @Size(max = 50, message = "Số tài khoản không được vượt quá 50 ký tự")
    private String vietQrAccountNo;

    @Size(max = 160, message = "Tên chủ tài khoản không được vượt quá 160 ký tự")
    private String vietQrAccountName;

    @Size(max = 30, message = "Mẫu VietQR không được vượt quá 30 ký tự")
    private String vietQrTemplate;

    @Size(max = 50, message = "Tiền tố nội dung chuyển khoản không được vượt quá 50 ký tự")
    private String vietQrDescriptionPrefix;

    // Tích điểm
    @DecimalMin(value = "1", message = "Số tiền để nhận 1 điểm phải lớn hơn 0")
    private BigDecimal loyaltyMoneyPerEarnedPoint;

    @DecimalMin(value = "1", message = "Giá trị quy đổi của 1 điểm phải lớn hơn 0")
    private BigDecimal loyaltyValuePerRedeemedPoint;

    @Min(value = 1, message = "Số điểm tối thiểu khi sử dụng phải từ 1")
    @Max(value = 1000000, message = "Số điểm tối thiểu khi sử dụng quá lớn")
    private Integer loyaltyMinimumRedeemPoints;

    @DecimalMin(value = "0.01", message = "Tỷ lệ sử dụng điểm phải từ 1%")
    @DecimalMax(value = "1.00", message = "Tỷ lệ sử dụng điểm không được vượt quá 100%")
    private BigDecimal loyaltyMaximumRedeemRatio;

    // Chatbot AI - API key tiếp tục lấy từ biến môi trường, không nhận qua API này.
    private Boolean chatbotEnabled;

    @Size(max = 120, message = "Tên model chatbot không được vượt quá 120 ký tự")
    private String chatbotModel;

    @Min(value = 5, message = "Timeout chatbot tối thiểu là 5 giây")
    @Max(value = 120, message = "Timeout chatbot tối đa là 120 giây")
    private Integer chatbotTimeoutSeconds;

    @Min(value = 100, message = "Giới hạn đầu ra chatbot tối thiểu là 100 token")
    @Max(value = 10000, message = "Giới hạn đầu ra chatbot tối đa là 10000 token")
    private Integer chatbotMaxOutputTokens;

    @Min(value = 0, message = "Số tin nhắn lịch sử không được âm")
    @Max(value = 50, message = "Số tin nhắn lịch sử không được vượt quá 50")
    private Integer chatbotMaxHistoryMessages;

    @DecimalMin(value = "0.00", message = "Độ tin cậy tối thiểu phải từ 0")
    @DecimalMax(value = "1.00", message = "Độ tin cậy tối thiểu không được vượt quá 1")
    private BigDecimal chatbotMinimumConfidence;
}
