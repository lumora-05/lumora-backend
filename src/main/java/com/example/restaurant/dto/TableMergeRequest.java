package com.example.restaurant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TableMergeRequest(
        @NotNull(message = "Mã bàn chính không được để trống")
        Integer maBanChinh,

        @NotEmpty(message = "Phải chọn ít nhất một bàn để ghép")
        List<@NotNull(message = "Mã bàn ghép không được để trống") Integer> maBanGhep
) {
}
