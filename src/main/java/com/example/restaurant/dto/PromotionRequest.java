package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionRequest(
        @NotBlank @Size(max = 50) String maCode,
        @NotBlank @Size(max = 100) String tenKhuyenMai,
        @Size(max = 150) String tenKhuyenMaiEn,
        @Size(max = 255) String moTa,
        @Size(max = 500) String moTaEn,
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
