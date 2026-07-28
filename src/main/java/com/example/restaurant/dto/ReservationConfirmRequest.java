package com.example.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReservationConfirmRequest(
        @NotNull(message = "Vui lòng chọn bàn dự kiến")
        Integer maBanDuKien,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
        String ghiChu
) {}
