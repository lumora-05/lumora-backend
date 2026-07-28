package com.example.restaurant.dto;

public record InventoryWasteResponse(
        InventoryTransactionResponse giaoDich,
        IngredientResponse nguyenLieu,
        IngredientBatchResponse loNguyenLieu
) {
}
