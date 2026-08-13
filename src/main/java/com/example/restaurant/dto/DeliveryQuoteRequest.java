package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryQuoteRequest(
        @NotBlank @Size(max = 100) String tinhThanh,
        /** Trường tương thích với client cũ; không còn bắt buộc trong mô hình địa chỉ 2 cấp. */
        @Size(max = 100) String quanHuyen,
        @NotBlank @Size(max = 120) String phuongXa,
        @Size(max = 50) String soNha,
        @Size(max = 200) String tenDuong,
        @Size(max = 500) String thongTinDiaChi,
        /** Trường tương thích với client cũ; client mới dùng soNha + tenDuong. */
        @Size(max = 500) String diaChiChiTiet,
        @Size(max = 255) String googlePlaceId,
        @Size(max = 700) String googleFormattedAddress
) {
}
