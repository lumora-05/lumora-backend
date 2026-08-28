package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationDepositVietQrResponse(
        Integer maDatBan,
        String maTraCuu,
        BigDecimal amount,
        String depositStatus,
        LocalDateTime paymentDeadline,
        String bankId,
        String bankName,
        String accountNo,
        String accountName,
        String addInfo,
        String template,
        String qrUrl
) {}
