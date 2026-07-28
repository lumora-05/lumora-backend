package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.FoodRequest;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.entity.Food;
import com.example.restaurant.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Food>>> activeMenu() {
        List<Food> foods = menuService.findActiveFoods();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thực đơn đang bán thành công", foods));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
    public ResponseEntity<ApiResponse<List<Food>>> allFoods() {
        List<Food> foods = menuService.findAll();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tất cả món ăn thành công", foods));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
    public ResponseEntity<ApiResponse<PageResponse<Food>>> pagedFoods(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean active) {
        PageResponse<Food> result = PageResponse.from(
                menuService.findPage(page, size, keyword, categoryId, active)
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách món ăn phân trang thành công", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Food>> detail(@PathVariable Integer id) {
        Food food = menuService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết món ăn thành công", food));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<Food>>> byCategory(@PathVariable Integer categoryId) {
        List<Food> foods = menuService.findByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách món ăn theo danh mục thành công", foods));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Food>> create(@Valid @RequestBody FoodRequest request) {
        Food food = menuService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Thêm món ăn thành công", food));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
    public ResponseEntity<ApiResponse<Food>> update(@PathVariable Integer id,
                                                     @Valid @RequestBody FoodRequest request,
                                                     Authentication authentication) {
        Food food;
        if (hasRole(authentication, "ROLE_KITCHEN") && !hasRole(authentication, "ROLE_ADMIN")) {
            // Frontend bếp đang dùng PUT /menu/{id} để báo hết/mở lại món.
            // Với quyền KITCHEN, backend chỉ nhận trường trạng thái và không cho sửa tên, giá, danh mục...
            food = menuService.updateAvailability(id, request.trangThai());
        } else {
            food = menuService.update(id, request);
        }
        return ResponseEntity.ok(ApiResponse.success("Cập nhật món ăn thành công", food));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        menuService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa món ăn thành công"));
    }
}
