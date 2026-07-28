package com.example.restaurant.service;

import com.example.restaurant.dto.AdminNotificationResponse;
import com.example.restaurant.entity.AdminNotification;
import com.example.restaurant.entity.Ingredient;
import com.example.restaurant.repository.AdminNotificationRepository;
import com.example.restaurant.repository.IngredientBatchRepository;
import com.example.restaurant.repository.IngredientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminNotificationService {
    public static final String LOW_STOCK = "NGUYEN_LIEU_SAP_HET";
    public static final String OUT_OF_STOCK = "NGUYEN_LIEU_HET_HANG";

    private static final String IN_STOCK_STATUS = "CON_HANG";
    private static final String LOW_STOCK_STATUS = "SAP_HET";
    private static final String OUT_OF_STOCK_STATUS = "HET_HANG";

    private final AdminNotificationRepository notificationRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientBatchRepository batchRepository;
    private final RealtimeNotificationService realtimeNotificationService;

    public AdminNotificationService(AdminNotificationRepository notificationRepository,
                                    IngredientRepository ingredientRepository,
                                    IngredientBatchRepository batchRepository,
                                    RealtimeNotificationService realtimeNotificationService) {
        this.notificationRepository = notificationRepository;
        this.ingredientRepository = ingredientRepository;
        this.batchRepository = batchRepository;
        this.realtimeNotificationService = realtimeNotificationService;
    }

    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> findPage(int page, int size, Boolean unread,
                                                     String type, String keyword) {
        Specification<AdminNotification> specification = (root, query, cb) -> cb.conjunction();

        if (unread != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("daDoc"), !unread));
        }
        if (type != null && !type.isBlank()) {
            String normalizedType = normalizeType(type);
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("loaiThongBao"), normalizedType));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("tieuDe")), pattern),
                    cb.like(cb.lower(root.<String>get("noiDung")), pattern),
                    cb.like(cb.lower(root.<String>get("tenNguyenLieu")), pattern)
            ));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("thoiGianTao"), Sort.Order.desc("maThongBao"))
        );
        return notificationRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        return notificationRepository.countByDaDocFalse();
    }

    @Transactional
    public AdminNotificationResponse markAsRead(Long id) {
        AdminNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy thông báo: " + id));
        markRead(notification, LocalDateTime.now());
        return toResponse(notificationRepository.saveAndFlush(notification));
    }

    @Transactional
    public int markAllAsRead() {
        return notificationRepository.markAllAsRead(LocalDateTime.now());
    }

    /**
     * Kiểm tra trạng thái sau mỗi thao tác kho. Một nguyên liệu chỉ có tối đa một
     * cảnh báo chưa đọc cho cùng trạng thái, nhờ đó không bị lặp thông báo.
     */
    @Transactional
    public boolean evaluateStock(Ingredient ingredient, String previousStockStatus) {
        if (ingredient == null || ingredient.getMaNguyenLieu() == null) {
            return false;
        }

        String currentStatus = calculateStockStatus(ingredient);
        if (!Boolean.TRUE.equals(ingredient.getTrangThai()) || IN_STOCK_STATUS.equals(currentStatus)) {
            resolveUnreadAlerts(ingredient.getMaNguyenLieu());
            if (previousStockStatus != null
                    && !IN_STOCK_STATUS.equals(previousStockStatus)
                    && IN_STOCK_STATUS.equals(currentStatus)) {
                realtimeNotificationService.notifyAdminInventoryRecovered(toInventoryPayload(ingredient));
            }
            return false;
        }

        if (previousStockStatus != null && !previousStockStatus.equals(currentStatus)) {
            resolveUnreadAlerts(ingredient.getMaNguyenLieu());
        }

        boolean exists = notificationRepository
                .findFirstByMaNguyenLieuAndTrangThaiTonKhoAndDaDocFalseOrderByThoiGianTaoDesc(
                        ingredient.getMaNguyenLieu(), currentStatus)
                .isPresent();
        if (exists) {
            return false;
        }

        AdminNotification saved = notificationRepository.saveAndFlush(buildStockAlert(ingredient, currentStatus));
        realtimeNotificationService.notifyAdminInventoryAlert(toResponse(saved));
        return true;
    }

    /**
     * Tạo cảnh báo cho dữ liệu sắp hết/hết hàng đã tồn tại trước khi chức năng
     * thông báo được triển khai. Hàm có chống trùng nên an toàn khi chạy lại.
     */
    @Transactional
    public int synchronizeCurrentLowStock() {
        List<Ingredient> ingredients = ingredientRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("trangThai"), true),
                Sort.by(Sort.Order.asc("tenNguyenLieu"))
        );
        int created = 0;
        for (Ingredient ingredient : ingredients) {
            if (!IN_STOCK_STATUS.equals(calculateStockStatus(ingredient))
                    && evaluateStock(ingredient, null)) {
                created++;
            }
        }
        return created;
    }

    private AdminNotification buildStockAlert(Ingredient ingredient, String stockStatus) {
        BigDecimal stock = calculateUsableStock(ingredient);
        BigDecimal minimum = safe(ingredient.getMucTonToiThieu());
        String unit = ingredient.getDonViTinh() == null ? "" : ingredient.getDonViTinh().trim();

        AdminNotification notification = new AdminNotification();
        notification.setLoaiThongBao(OUT_OF_STOCK_STATUS.equals(stockStatus)
                ? OUT_OF_STOCK : LOW_STOCK);
        notification.setTieuDe(OUT_OF_STOCK_STATUS.equals(stockStatus)
                ? "Nguyên liệu đã hết hàng"
                : "Nguyên liệu sắp hết");
        notification.setNoiDung(buildMessage(
                ingredient.getTenNguyenLieu(), stock, minimum, unit, stockStatus));
        notification.setMaNguyenLieu(ingredient.getMaNguyenLieu());
        notification.setTenNguyenLieu(ingredient.getTenNguyenLieu());
        notification.setSoLuongTon(stock);
        notification.setMucTonToiThieu(minimum);
        notification.setDonViTinh(unit);
        notification.setTrangThaiTonKho(stockStatus);
        notification.setDaDoc(false);
        return notification;
    }

    private String buildMessage(String name, BigDecimal stock, BigDecimal minimum,
                                String unit, String stockStatus) {
        String unitSuffix = unit.isBlank() ? "" : " " + unit;
        if (OUT_OF_STOCK_STATUS.equals(stockStatus)) {
            return name + " đã hết hàng. Tồn kho hiện tại: " + strip(stock) + unitSuffix
                    + ", mức tối thiểu: " + strip(minimum) + unitSuffix + ".";
        }
        return name + " chỉ còn " + strip(stock) + unitSuffix
                + ", đã chạm hoặc thấp hơn mức tối thiểu "
                + strip(minimum) + unitSuffix + ".";
    }

    private void resolveUnreadAlerts(Integer ingredientId) {
        LocalDateTime now = LocalDateTime.now();
        List<AdminNotification> notifications =
                notificationRepository.findAllByMaNguyenLieuAndDaDocFalse(ingredientId);
        if (notifications.isEmpty()) {
            return;
        }
        notifications.forEach(notification -> markRead(notification, now));
        notificationRepository.saveAll(notifications);
        notificationRepository.flush();
    }

    private void markRead(AdminNotification notification, LocalDateTime readAt) {
        if (!Boolean.TRUE.equals(notification.getDaDoc())) {
            notification.setDaDoc(true);
            notification.setThoiGianDoc(readAt);
        }
    }

    private String calculateStockStatus(Ingredient ingredient) {
        BigDecimal stock = calculateUsableStock(ingredient);
        BigDecimal minimum = safe(ingredient.getMucTonToiThieu());
        if (stock.compareTo(BigDecimal.ZERO) <= 0) {
            return OUT_OF_STOCK_STATUS;
        }
        if (stock.compareTo(minimum) <= 0) {
            return LOW_STOCK_STATUS;
        }
        return IN_STOCK_STATUS;
    }

    private BigDecimal calculateUsableStock(Ingredient ingredient) {
        BigDecimal physical = safe(ingredient.getSoLuongTon());
        BigDecimal tracked = safe(batchRepository.sumRemainingByIngredient(
                ingredient.getMaNguyenLieu()));
        BigDecimal usableBatch = safe(batchRepository.sumUsableRemainingByIngredient(
                ingredient.getMaNguyenLieu(), LocalDate.now()));
        BigDecimal untracked = physical.subtract(tracked).max(BigDecimal.ZERO);
        return untracked.add(usableBatch).min(physical);
    }

    private Object toInventoryPayload(Ingredient ingredient) {
        return new AdminNotificationResponse(
                null,
                "NGUYEN_LIEU_DA_BO_SUNG",
                "Tồn kho đã được bổ sung",
                ingredient.getTenNguyenLieu() + " đã cao hơn mức tồn tối thiểu.",
                ingredient.getMaNguyenLieu(),
                ingredient.getTenNguyenLieu(),
                calculateUsableStock(ingredient),
                safe(ingredient.getMucTonToiThieu()),
                ingredient.getDonViTinh(),
                IN_STOCK_STATUS,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private AdminNotificationResponse toResponse(AdminNotification notification) {
        return new AdminNotificationResponse(
                notification.getMaThongBao(),
                notification.getLoaiThongBao(),
                notification.getTieuDe(),
                notification.getNoiDung(),
                notification.getMaNguyenLieu(),
                notification.getTenNguyenLieu(),
                notification.getSoLuongTon(),
                notification.getMucTonToiThieu(),
                notification.getDonViTinh(),
                notification.getTrangThaiTonKho(),
                notification.getDaDoc(),
                notification.getThoiGianTao(),
                notification.getThoiGianDoc()
        );
    }

    private String normalizeType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case LOW_STOCK, "SAP_HET", "LOW_STOCK" -> LOW_STOCK;
            case OUT_OF_STOCK, "HET_HANG", "OUT_OF_STOCK" -> OUT_OF_STOCK;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Loại thông báo không hợp lệ: " + value);
        };
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String strip(BigDecimal value) {
        return safe(value).stripTrailingZeros().toPlainString();
    }
}
