package com.example.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FoodRecipeUpdateRequest(
        @NotNull List<@Valid FoodRecipeIngredientRequest> nguyenLieu
) {
}
