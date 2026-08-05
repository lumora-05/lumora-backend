package com.example.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationPreorderRequest(
        String ghiChu,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Integer maMonAn,
            @NotNull @Min(1) Integer soLuong,
            String ghiChu
    ) {}
}
