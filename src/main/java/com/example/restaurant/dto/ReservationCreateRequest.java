package com.example.restaurant.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record ReservationCreateRequest(
        @NotBlank(message = "Họ tên khách không được để trống")
        @Size(max = 100, message = "Họ tên khách không được vượt quá 100 ký tự")
        String hoTenKhach,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
                regexp = "^0(?:3[2-9]|5[2689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}$",
                message = "Số điện thoại phải là số di động Việt Nam hợp lệ gồm 10 chữ số"
        )
        String soDienThoai,

        @NotNull(message = "Ngày giờ đến không được để trống")
        @Future(message = "Ngày giờ đến phải ở tương lai")
        LocalDateTime ngayGioDen,

        @NotNull(message = "Số lượng khách không được để trống")
        @Min(value = 1, message = "Số lượng khách phải từ 1")
        @Max(value = 50, message = "Số lượng khách không được vượt quá 50")
        Integer soLuongKhach,

        @Size(max = 100, message = "Khu vực mong muốn không được vượt quá 100 ký tự")
        String khuVucMongMuon,

        @Min(value = 30, message = "Thời lượng đặt bàn tối thiểu là 30 phút")
        @Max(value = 360, message = "Thời lượng đặt bàn tối đa là 360 phút")
        Integer thoiLuongPhut,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
        String ghiChu
) {}
