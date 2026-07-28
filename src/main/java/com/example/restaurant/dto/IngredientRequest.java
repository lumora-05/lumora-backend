package com.example.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record IngredientRequest(
        @NotBlank(message = "Tên nguyên liệu không được để trống")
        @Size(max = 150, message = "Tên nguyên liệu không được vượt quá 150 ký tự")
        @JsonAlias({"name"})
        String tenNguyenLieu,

        @NotBlank(message = "Đơn vị tính không được để trống")
        @Size(max = 30, message = "Đơn vị tính không được vượt quá 30 ký tự")
        @JsonAlias({"unit"})
        String donViTinh,

        @DecimalMin(value = "0", message = "Số lượng tồn không được âm")
        @JsonAlias({"stockQuantity", "quantity"})
        BigDecimal soLuongTon,

        @DecimalMin(value = "0", message = "Mức tồn tối thiểu không được âm")
        @JsonAlias({"minimumStock", "minStock"})
        BigDecimal mucTonToiThieu,

        @DecimalMin(value = "0", message = "Giá nhập không được âm")
        @JsonAlias({"importPrice", "price"})
        BigDecimal giaNhap,

        @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
        @JsonAlias({"description"})
        String moTa,

        @JsonAlias({"active"})
        Boolean trangThai
) {
}
