package com.example.restaurant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReservationPreorderConfirmRequest(
        @Min(15) @Max(180) Integer soPhutChuanBiTruoc,
        String ghiChu
) {}
