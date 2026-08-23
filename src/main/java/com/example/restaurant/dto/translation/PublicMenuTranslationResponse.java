package com.example.restaurant.dto.translation;

import java.util.List;

public record PublicMenuTranslationResponse(
        String targetLanguage,
        List<FoodItem> foods,
        List<CategoryItem> categories
) {
    public record FoodItem(
            Integer maMonAn,
            String tenMonAn,
            String moTa
    ) {
    }

    public record CategoryItem(
            Integer maDanhMuc,
            String tenDanhMuc,
            String moTa
    ) {
    }
}
