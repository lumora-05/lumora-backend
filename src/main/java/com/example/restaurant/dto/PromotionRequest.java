package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionRequest(
        @NotBlank String maCode,
        @NotBlank String tenKhuyenMai,
        String moTa,
        @NotBlank String loaiGiam,
        @NotNull @DecimalMin("0.01") BigDecimal giaTriGiam,
        @DecimalMin("0.0") BigDecimal giaTriDonToiThieu,
        @DecimalMin("0.01") BigDecimal giamToiDa,
        @Min(1) Integer gioiHanSuDung,
        @NotNull LocalDate ngayBatDau,
        @NotNull LocalDate ngayKetThuc,
        Boolean trangThai
) {
}
