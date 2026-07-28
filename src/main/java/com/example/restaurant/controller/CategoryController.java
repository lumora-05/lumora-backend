package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.CategoryRequest;
import com.example.restaurant.dto.CategoryResponse;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.entity.Category;
import com.example.restaurant.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Category>>> activeCategories() {
        List<Category> categories = categoryService.findActive();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục đang hoạt động thành công", categories));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> allCategories() {
        List<CategoryResponse> categories = categoryService.findAllWithFoodCount();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục món ăn thành công", categories));
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> pagedCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        PageResponse<CategoryResponse> result = PageResponse.from(
                categoryService.findPage(page, size, keyword, active)
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục phân trang thành công", result));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Category>> create(@Valid @RequestBody CategoryRequest request) {
        Category category = categoryService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Thêm danh mục món ăn thành công", category));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Category>> update(@PathVariable Integer id, @Valid @RequestBody CategoryRequest request) {
        Category category = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục món ăn thành công", category));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh mục món ăn thành công"));
    }
}
