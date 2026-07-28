package com.example.restaurant.dto;

import jakarta.validation.constraints.NotNull;

public record ReservationAssignTableRequest(
        @NotNull(message = "Vui lòng chọn bàn thực tế")
        Integer maBan
) {}
