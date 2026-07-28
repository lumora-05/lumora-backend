package com.example.restaurant.service;

import com.example.restaurant.dto.FoodRequest;
import com.example.restaurant.entity.Category;
import com.example.restaurant.entity.Food;
import com.example.restaurant.repository.CategoryRepository;
import com.example.restaurant.repository.FoodRepository;
import com.example.restaurant.repository.OrderItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class MenuService {
    private final FoodRepository foodRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final SystemActivityService systemActivityService;
    private final RealtimeNotificationService realtimeNotificationService;

    public MenuService(FoodRepository foodRepository,
                       CategoryRepository categoryRepository,
                       OrderItemRepository orderItemRepository,
                       SystemActivityService systemActivityService,
                       RealtimeNotificationService realtimeNotificationService) {
        this.foodRepository = foodRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
        this.systemActivityService = systemActivityService;
        this.realtimeNotificationService = realtimeNotificationService;
    }

    public List<Food> findActiveFoods() {
        return foodRepository.findByTrangThaiTrue();
    }

    public List<Food> findAll() {
        return foodRepository.findAll();
    }

    /**
     * Phân trang thực đơn cho khách: chỉ trả các món đang bán.
     * Tìm kiếm và lọc danh mục được thực hiện trực tiếp tại database.
     */
    @Transactional(readOnly = true)
    public Page<Food> findCustomerPage(int page,
                                       int size,
                                       String keyword,
                                       Integer categoryId) {
        return findPage(page, size, keyword, categoryId, true);
    }

    @Transactional(readOnly = true)
    public Page<Food> findPage(int page,
                               int size,
                               String keyword,
                               Integer categoryId,
                               Boolean active) {
        Specification<Food> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("tenMonAn")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("moTa")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.join("danhMuc").get("tenDanhMuc")), pattern)
            ));
        }
        if (categoryId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("danhMuc").get("maDanhMuc"), categoryId));
        }
        if (active != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("trangThai"), active));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("ngayTao"), Sort.Order.desc("maMonAn"))
        );
        return foodRepository.findAll(specification, pageable);
    }

    public List<Food> findByCategory(Integer categoryId) {
        return foodRepository.findByDanhMuc_MaDanhMucAndTrangThaiTrue(categoryId);
    }

    public Food findById(Integer id) {
        return foodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món ăn: " + id));
    }

    @Transactional
    public Food create(FoodRequest request) {
        Food food = new Food();
        apply(food, request);
        Food savedFood = foodRepository.save(food);
        systemActivityService.record(
                "FOOD_CREATED",
                "Món ăn " + savedFood.getTenMonAn() + " đã được thêm vào thực đơn",
                savedFood.getMaMonAn()
        );
        realtimeNotificationService.notifyDashboardRefresh(savedFood);
        return savedFood;
    }

    @Transactional
    public Food update(Integer id, FoodRequest request) {
        Food food = findById(id);
        apply(food, request);
        Food savedFood = foodRepository.save(food);
        systemActivityService.record(
                "FOOD_UPDATED",
                "Món ăn " + savedFood.getTenMonAn() + " đã được cập nhật",
                savedFood.getMaMonAn()
        );
        realtimeNotificationService.notifyDashboardRefresh(savedFood);
        return savedFood;
    }

    @Transactional
    public Food updateAvailability(Integer id, Boolean active) {
        if (active == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái món ăn không được để trống");
        }

        Food food = findById(id);
        if (active.equals(food.getTrangThai())) {
            return food;
        }

        food.setTrangThai(active);
        Food savedFood = foodRepository.save(food);
        systemActivityService.record(
                active ? "FOOD_AVAILABLE" : "FOOD_OUT_OF_STOCK",
                "Món ăn " + savedFood.getTenMonAn()
                        + (active ? " đã được mở bán lại" : " đã được bếp báo hết"),
                savedFood.getMaMonAn()
        );
        realtimeNotificationService.notifyMenuAvailabilityChanged(savedFood);
        realtimeNotificationService.notifyDashboardRefresh(savedFood);
        return savedFood;
    }

    @Transactional
    public void delete(Integer id) {
        Food food = findById(id);

        if (orderItemRepository.existsByMonAn_MaMonAn(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món ăn đã phát sinh trong đơn hàng nên không thể xóa vĩnh viễn. "
                            + "Hãy chuyển món sang trạng thái ngừng bán để bảo toàn lịch sử đơn hàng"
            );
        }

        String foodName = food.getTenMonAn();
        foodRepository.delete(food);
        foodRepository.flush();

        systemActivityService.record(
                "FOOD_DELETED",
                "Món ăn " + foodName + " đã bị xóa khỏi hệ thống",
                id
        );
        Map<String, Object> payload = Map.of(
                "maMonAn", id,
                "tenMonAn", foodName,
                "deleted", true
        );
        realtimeNotificationService.notifyMenuAvailabilityChanged(payload);
        realtimeNotificationService.notifyDashboardRefresh(payload);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private void apply(Food food, FoodRequest request) {
        Category category = categoryRepository.findById(request.maDanhMuc())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục: " + request.maDanhMuc()));
        if (!Boolean.TRUE.equals(category.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục món ăn đang bị khóa");
        }
        food.setDanhMuc(category);
        food.setTenMonAn(request.tenMonAn());
        food.setGia(request.gia());
        food.setMoTa(request.moTa());
        food.setHinhAnh(request.hinhAnh());
        if (request.trangThai() != null) {
            food.setTrangThai(request.trangThai());
        }
    }
}
