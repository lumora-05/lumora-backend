package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.FoodRecipeResponse;
import com.example.restaurant.dto.FoodRecipeUpdateRequest;
import com.example.restaurant.service.FoodRecipeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
@PreAuthorize("hasRole('ADMIN')")
public class FoodRecipeController {
    private final FoodRecipeService foodRecipeService;

    public FoodRecipeController(FoodRecipeService foodRecipeService) {
        this.foodRecipeService = foodRecipeService;
    }

    @GetMapping("/{foodId}/recipe")
    public ResponseEntity<ApiResponse<FoodRecipeResponse>> findRecipe(@PathVariable Integer foodId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy công thức món ăn thành công",
                foodRecipeService.findByFoodId(foodId)
        ));
    }

    @PutMapping("/{foodId}/recipe")
    public ResponseEntity<ApiResponse<FoodRecipeResponse>> updateRecipe(
            @PathVariable Integer foodId,
            @Valid @RequestBody FoodRecipeUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật công thức món ăn thành công",
                foodRecipeService.replaceRecipe(foodId, request, authentication.getName())
        ));
    }
}
