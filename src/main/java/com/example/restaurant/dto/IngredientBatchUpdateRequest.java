package com.example.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IngredientBatchUpdateRequest(
        @NotBlank(message = "Số lô không được để trống")
        @Size(max = 80, message = "Số lô không được vượt quá 80 ký tự")
        @JsonAlias({"batchNumber", "lotNumber"})
        String soLo,

        @JsonAlias({"importDate", "receivedDate"})
        LocalDate ngayNhap,

        @JsonAlias({"manufacturingDate", "productionDate"})
        LocalDate ngaySanXuat,

        @JsonAlias({"expiryDate", "expirationDate"})
        LocalDate hanSuDung,

        @DecimalMin(value = "0", message = "Đơn giá nhập không được âm")
        @JsonAlias({"unitPrice", "price"})
        BigDecimal donGiaNhap,

        @Size(max = 200, message = "Nhà cung cấp không được vượt quá 200 ký tự")
        @JsonAlias({"supplier"})
        String nhaCungCap,

        @JsonAlias({"active"})
        Boolean trangThai
) {
}
