package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.util.List;

public record InventoryWasteStatisticsResponse(
        long soLanTieuHuy,
        long soNguyenLieuAnhHuong,
        long soLoAnhHuong,
        BigDecimal tongGiaTriTieuHuy,
        List<InventoryWasteReasonStatisticsResponse> theoLyDo
) {
}
