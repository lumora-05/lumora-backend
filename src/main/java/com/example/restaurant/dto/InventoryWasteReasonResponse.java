package com.example.restaurant.dto;

public record InventoryWasteReasonResponse(
        String maLyDo,
        String tenLyDo,
        boolean batBuocGhiChu,
        boolean chiDungChoLoHetHan
) {
}
