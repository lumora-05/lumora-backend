package com.example.restaurant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TableRequest(
        @NotBlank(message = "Tên bàn không được để trống")
        @Size(max = 50, message = "Tên bàn tối đa 50 ký tự")
        String tenBan,

        // Giữ lại để tương thích với client cũ; backend không cho client tự gán mã QR.
        String maQr,

        String trangThai,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String ghiChu,

        @Size(max = 100, message = "Khu vực tối đa 100 ký tự")
        String khuVuc,

        // Frontend hiện gửi đồng thời khuVuc và tenKhuVuc.
        @Size(max = 100, message = "Tên khu vực tối đa 100 ký tự")
        String tenKhuVuc,

        @Min(value = 1, message = "Sức chứa phải từ 1 khách")
        @Max(value = 100, message = "Sức chứa tối đa 100 khách")
        Integer sucChua
) {
}
