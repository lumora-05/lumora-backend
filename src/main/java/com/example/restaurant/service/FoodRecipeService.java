package com.example.restaurant.service;

import com.example.restaurant.dto.FoodRecipeIngredientRequest;
import com.example.restaurant.dto.FoodRecipeIngredientResponse;
import com.example.restaurant.dto.FoodRecipeResponse;
import com.example.restaurant.dto.FoodRecipeUpdateRequest;
import com.example.restaurant.entity.Food;
import com.example.restaurant.entity.FoodRecipeIngredient;
import com.example.restaurant.entity.Ingredient;
import com.example.restaurant.repository.FoodRecipeIngredientRepository;
import com.example.restaurant.repository.FoodRepository;
import com.example.restaurant.repository.IngredientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FoodRecipeService {
    private final FoodRepository foodRepository;
    private final IngredientRepository ingredientRepository;
    private final FoodRecipeIngredientRepository recipeRepository;
    private final SystemActivityService systemActivityService;

    public FoodRecipeService(FoodRepository foodRepository,
                             IngredientRepository ingredientRepository,
                             FoodRecipeIngredientRepository recipeRepository,
                             SystemActivityService systemActivityService) {
        this.foodRepository = foodRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.systemActivityService = systemActivityService;
    }

    @Transactional(readOnly = true)
    public FoodRecipeResponse findByFoodId(Integer foodId) {
        Food food = requireFood(foodId);
        List<FoodRecipeIngredient> recipe = recipeRepository.findAllByFoodId(foodId);
        return toResponse(food, recipe);
    }

    /**
     * Thay thế toàn bộ công thức của món trong một transaction. Danh sách rỗng
     * được hiểu là xóa công thức; không tác động tới món ăn hoặc dữ liệu kho.
     */
    @Transactional
    public FoodRecipeResponse replaceRecipe(Integer foodId,
                                            FoodRecipeUpdateRequest request,
                                            String username) {
        Food food = requireFood(foodId);
        List<FoodRecipeIngredientRequest> requestedItems = request == null || request.nguyenLieu() == null
                ? List.of()
                : request.nguyenLieu();

        Set<Integer> ingredientIds = new HashSet<>();
        List<FoodRecipeIngredient> replacements = new ArrayList<>();
        for (FoodRecipeIngredientRequest item : requestedItems) {
            if (item == null || item.maNguyenLieu() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mỗi dòng công thức phải chọn nguyên liệu");
            }
            if (!ingredientIds.add(item.maNguyenLieu())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Một nguyên liệu chỉ được xuất hiện một lần trong công thức");
            }
            BigDecimal quantity = item.dinhLuong();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Định lượng nguyên liệu phải lớn hơn 0");
            }

            Ingredient ingredient = ingredientRepository.findById(item.maNguyenLieu())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy nguyên liệu: " + item.maNguyenLieu()
                    ));
            if (!Boolean.TRUE.equals(ingredient.getTrangThai())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Nguyên liệu đang ngừng sử dụng: " + ingredient.getTenNguyenLieu());
            }

            FoodRecipeIngredient recipeItem = new FoodRecipeIngredient();
            recipeItem.setMonAn(food);
            recipeItem.setNguyenLieu(ingredient);
            recipeItem.setDinhLuong(quantity);
            recipeItem.setTrangThai(true);
            replacements.add(recipeItem);
        }

        recipeRepository.deleteByMonAn_MaMonAn(foodId);
        recipeRepository.flush();
        if (!replacements.isEmpty()) {
            recipeRepository.saveAll(replacements);
            recipeRepository.flush();
        }

        systemActivityService.record(
                "FOOD_RECIPE_UPDATED",
                "Công thức món " + food.getTenMonAn() + " đã được cập nhật với "
                        + replacements.size() + " nguyên liệu bởi " + displayActor(username),
                foodId
        );
        return toResponse(food, recipeRepository.findAllByFoodId(foodId));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveRecipe(Integer foodId) {
        return recipeRepository.existsByMonAn_MaMonAnAndTrangThaiTrue(foodId);
    }

    private Food requireFood(Integer foodId) {
        return foodRepository.findById(foodId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy món ăn: " + foodId
                ));
    }

    private FoodRecipeResponse toResponse(Food food, List<FoodRecipeIngredient> recipe) {
        List<FoodRecipeIngredientResponse> items = recipe.stream()
                .map(item -> new FoodRecipeIngredientResponse(
                        item.getMaCongThuc(),
                        item.getNguyenLieu().getMaNguyenLieu(),
                        item.getNguyenLieu().getTenNguyenLieu(),
                        item.getNguyenLieu().getDonViTinh(),
                        item.getDinhLuong(),
                        item.getTrangThai()
                ))
                .toList();
        return new FoodRecipeResponse(
                food.getMaMonAn(),
                food.getTenMonAn(),
                !items.isEmpty(),
                items
        );
    }

    private String displayActor(String username) {
        return username == null || username.isBlank() ? "Hệ thống" : username;
    }
}
