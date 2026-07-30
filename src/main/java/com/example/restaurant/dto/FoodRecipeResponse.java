package com.example.restaurant.dto;

import java.util.List;

public record FoodRecipeResponse(
        Integer maMonAn,
        String tenMonAn,
        boolean daThietLap,
        List<FoodRecipeIngredientResponse> nguyenLieu
) {
}
