package com.example.restaurant.dto;

import java.math.BigDecimal;

public record InventoryWasteReasonStatisticsResponse(
        String maLyDo,
        String tenLyDo,
        long soLan,
        BigDecimal giaTriTieuHuy
) {
}
