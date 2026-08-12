package com.example.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record DeliveryOrderCreateRequest(
        @NotBlank @Size(max = 100) String clientRequestId,
        @NotBlank @Size(max = 120) String tenNguoiNhan,
        @NotBlank
        @Pattern(regexp = "^[0-9+ .()-]{9,20}$", message = "Số điện thoại không hợp lệ")
        String soDienThoaiNhan,
        @Email(message = "Email không đúng định dạng") @Size(max = 120) String emailNguoiNhan,
        @Size(max = 50) String soNha,
        @Size(max = 200) String tenDuong,
        @NotBlank @Size(max = 120) String phuongXa,
        @NotBlank @Size(max = 100) String quanHuyen,
        @NotBlank @Size(max = 100) String tinhThanh,
        @Size(max = 500) String thongTinDiaChi,
        /** Trường tương thích với client cũ; client mới dùng soNha + tenDuong. */
        @Size(max = 500) String diaChiChiTiet,
        @Size(max = 255) String googlePlaceId,
        @Size(max = 700) String googleFormattedAddress,
        @Size(max = 500) String ghiChuGiaoHang,
        @Size(max = 50) String maCodeKhuyenMai,
        @NotBlank @Size(max = 20) String phuongThucThanhToan,
        /** SOM_NHAT hoặc HEN_GIO. */
        @NotBlank @Size(max = 20) String loaiThoiGianNhan,
        LocalDateTime thoiGianNhanMongMuon,
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
