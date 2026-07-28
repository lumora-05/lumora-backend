package com.example.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InventoryWasteRequest(
        @NotNull(message = "Số lượng tiêu hủy không được để trống")
        @DecimalMin(value = "0.001", message = "Số lượng tiêu hủy phải lớn hơn 0")
        @JsonAlias({"quantity", "wasteQuantity"})
        BigDecimal soLuong,

        @NotBlank(message = "Lý do tiêu hủy không được để trống")
        @Size(max = 40, message = "Mã lý do không được vượt quá 40 ký tự")
        @JsonAlias({"reasonCode", "wasteReason"})
        String maLyDo,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
        @JsonAlias({"note", "reason", "description"})
        String ghiChu,

        @JsonAlias({"batchId", "lotId"})
        Long maLo
) {
}
