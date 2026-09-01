package com.example.restaurant.service;

import com.example.restaurant.dto.CategoryRequest;
import com.example.restaurant.dto.CategoryResponse;
import com.example.restaurant.entity.Category;
import com.example.restaurant.repository.CategoryRepository;
import com.example.restaurant.repository.FoodRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;

    public CategoryService(CategoryRepository categoryRepository, FoodRepository foodRepository) {
        this.categoryRepository = categoryRepository;
        this.foodRepository = foodRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAllWithFoodCount() {
        List<Category> categories = categoryRepository.findAll();
        Map<Integer, Long> countsByCategoryId = findFoodCounts(categories);
        return categories.stream()
                .map(category -> CategoryResponse.from(
                        category,
                        countsByCategoryId.getOrDefault(category.getMaDanhMuc(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> findPage(int page, int size, String keyword, Boolean active) {
        Specification<Category> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("tenDanhMuc")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("tenDanhMucEn")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("moTa")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("moTaEn")), pattern)
            ));
        }
        if (active != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("trangThai"), active));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("maDanhMuc"))
        );
        Page<Category> result = categoryRepository.findAll(specification, pageable);
        Map<Integer, Long> countsByCategoryId = findFoodCounts(result.getContent());
        return result.map(category -> CategoryResponse.from(
                category,
                countsByCategoryId.getOrDefault(category.getMaDanhMuc(), 0L)
        ));
    }

    public List<Category> findActive() {
        return categoryRepository.findByTrangThaiTrue();
    }

    public Category findById(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục: " + id));
    }

    public Category create(CategoryRequest request) {
        String normalizedName = normalizeName(request.tenDanhMuc());
        ensureCategoryNameAvailable(normalizedName, null);

        Category category = new Category();
        apply(category, request, normalizedName);
        return categoryRepository.save(category);
    }

    public Category update(Integer id, CategoryRequest request) {
        Category category = findById(id);
        String normalizedName = normalizeName(request.tenDanhMuc());
        ensureCategoryNameAvailable(normalizedName, id);

        apply(category, request, normalizedName);
        return categoryRepository.save(category);
    }

    public void delete(Integer id) {
        Category category = findById(id);
        categoryRepository.delete(category);
    }

    private Map<Integer, Long> findFoodCounts(Collection<Category> categories) {
        List<Integer> categoryIds = categories.stream()
                .map(Category::getMaDanhMuc)
                .filter(id -> id != null)
                .toList();

        if (categoryIds.isEmpty()) {
            return Map.of();
        }

        return foodRepository.countFoodsByCategoryIds(categoryIds).stream()
                .collect(Collectors.toMap(
                        FoodRepository.CategoryFoodCount::getMaDanhMuc,
                        FoodRepository.CategoryFoodCount::getSoMon,
                        (left, right) -> left
                ));
    }

    private void apply(Category category, CategoryRequest request, String normalizedName) {
        category.setTenDanhMuc(normalizedName);
        category.setMoTa(trimToNull(request.moTa()));
        if (request.trangThai() != null) {
            category.setTrangThai(request.trangThai());
        }
    }

    private void ensureCategoryNameAvailable(String normalizedName, Integer currentCategoryId) {
        boolean exists = currentCategoryId == null
                ? categoryRepository.existsByTenDanhMucIgnoreCase(normalizedName)
                : categoryRepository.existsByTenDanhMucIgnoreCaseAndMaDanhMucNot(normalizedName, currentCategoryId);
        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tên danh mục đã tồn tại. Vui lòng chọn tên khác."
            );
        }
    }

    private String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
