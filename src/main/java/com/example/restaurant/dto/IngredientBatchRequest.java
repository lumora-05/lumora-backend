package com.example.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IngredientBatchRequest(
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

        @NotNull(message = "Số lượng nhập không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng nhập phải lớn hơn 0")
        @JsonAlias({"quantity", "importQuantity"})
        BigDecimal soLuongNhap,

        @DecimalMin(value = "0", message = "Đơn giá nhập không được âm")
        @JsonAlias({"unitPrice", "price"})
        BigDecimal donGiaNhap,

        @Size(max = 200, message = "Nhà cung cấp không được vượt quá 200 ký tự")
        @JsonAlias({"supplier"})
        String nhaCungCap,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
        @JsonAlias({"note", "reason"})
        String ghiChu
) {
}
