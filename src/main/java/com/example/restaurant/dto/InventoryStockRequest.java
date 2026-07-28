package com.example.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryStockRequest(
        @NotBlank(message = "Loại giao dịch không được để trống")
        @JsonAlias({"type", "transactionType"})
        String loaiGiaoDich,

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0", message = "Số lượng không được âm")
        @JsonAlias({"quantity", "stockQuantity"})
        BigDecimal soLuong,

        @DecimalMin(value = "0", message = "Đơn giá nhập không được âm")
        @JsonAlias({"donGia", "unitPrice", "price"})
        BigDecimal donGiaNhap,

        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
        @JsonAlias({"ghiChu", "note", "reason"})
        String lyDo,

        @JsonAlias({"batchId", "lotId"})
        Long maLo,

        @Size(max = 80, message = "Số lô không được vượt quá 80 ký tự")
        @JsonAlias({"batchNumber", "lotNumber"})
        String soLo,

        @JsonAlias({"importDate", "receivedDate"})
        LocalDate ngayNhap,

        @JsonAlias({"manufacturingDate", "productionDate"})
        LocalDate ngaySanXuat,

        @JsonAlias({"expiryDate", "expirationDate"})
        LocalDate hanSuDung,

        @Size(max = 200, message = "Nhà cung cấp không được vượt quá 200 ký tự")
        @JsonAlias({"supplier"})
        String nhaCungCap
) {
}
