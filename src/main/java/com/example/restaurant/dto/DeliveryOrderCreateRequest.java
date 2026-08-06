package com.example.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DeliveryOrderCreateRequest(
        @NotBlank @Size(max = 100) String clientRequestId,
        @NotBlank @Size(max = 120) String tenNguoiNhan,
        @NotBlank
        @Pattern(regexp = "^[0-9+ .()-]{9,20}$", message = "Số điện thoại không hợp lệ")
        String soDienThoaiNhan,
        @NotBlank @Size(max = 500) String diaChiGiaoHang,
        @NotBlank @Size(max = 30) String khuVucGiaoHang,
        @Size(max = 500) String ghiChuGiaoHang,
        @NotBlank @Size(max = 20) String phuongThucThanhToan,
        @Size(max = 255) String ghiChuDonHang,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Integer maMonAn,
            @NotNull @Min(1) Integer soLuong,
            @Size(max = 255) String ghiChu
    ) {
    }
}
