package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Dữ liệu VietQR mà frontend dùng để hiển thị hoặc in trên phiếu tạm tính. */
public record VietQrResponse(
        Integer maDonHang,
        String bankId,
        String bankName,
        String accountNo,
        String accountName,
        BigDecimal amount,
        String addInfo,
        String template,
        String qrUrl,
        LocalDateTime paymentDeadline
) {
}
