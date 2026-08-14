package com.example.restaurant.dto;

/**
 * Một địa chỉ do backend/OpenRouteService gợi ý. Frontend hiển thị label và giữ
 * selectionToken; API key vẫn chỉ nằm ở backend.
 */
public record DeliveryAddressSuggestionResponse(
        String label,
        String tenDiaDiem,
        String soNha,
        String tenDuong,
        String phuongXa,
        String tinhThanh,
        Double latitude,
        Double longitude,
        String selectionToken
) {
}
