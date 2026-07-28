package com.example.restaurant.dto;

import java.math.BigDecimal;

public record IngredientBatchStatisticsResponse(
        long tongSoLo,
        long loDangSuDung,
        long loSapHetHan,
        long loDaHetHan,
        long loDaDungHet,
        long loKhongTheoDoiHan,
        BigDecimal giaTriLoDaHetHan
) {
}
