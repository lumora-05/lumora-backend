package com.example.restaurant.service;

import com.example.restaurant.dto.ApplyPromotionRequest;
import com.example.restaurant.dto.PromotionRequest;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.Promotion;
import com.example.restaurant.repository.OrderRepository;
import com.example.restaurant.repository.PromotionRepository;
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
import java.util.Set;

@Service
public class PromotionService {
    private static final Set<String> PROMOTION_EDITABLE_ORDER_STATUSES = Set.of(
            "DA_PHUC_VU",
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN"
    );

    private final PromotionRepository promotionRepository;
    private final OrderRepository orderRepository;
    private final OrderPricingService orderPricingService;
    private final SystemActivityService systemActivityService;
    private final RealtimeNotificationService realtimeNotificationService;

    public PromotionService(PromotionRepository promotionRepository,
                            OrderRepository orderRepository,
                            OrderPricingService orderPricingService,
                            SystemActivityService systemActivityService,
                            RealtimeNotificationService realtimeNotificationService) {
        this.promotionRepository = promotionRepository;
        this.orderRepository = orderRepository;
        this.orderPricingService = orderPricingService;
        this.systemActivityService = systemActivityService;
        this.realtimeNotificationService = realtimeNotificationService;
    }

    public List<Promotion> findAll() {
        return promotionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Promotion> findPage(int page,
                                    int size,
                                    String keyword,
                                    String status) {
        Specification<Promotion> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("tenKhuyenMai")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("maCode")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("loaiGiam")), pattern)
            ));
        }

        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        switch (normalizedStatus) {
            case "ACTIVE" -> specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.isTrue(root.get("trangThai")),
                    criteriaBuilder.lessThanOrEqualTo(root.get("ngayBatDau"), today),
                    criteriaBuilder.greaterThanOrEqualTo(root.get("ngayKetThuc"), today),
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("gioiHanSuDung")),
                            criteriaBuilder.isNull(root.get("soLuotDaDung")),
                            criteriaBuilder.lessThan(
                                    root.<Integer>get("soLuotDaDung"),
                                    root.<Integer>get("gioiHanSuDung")
                            )
                    )
            ));
            case "UPCOMING" -> specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.isTrue(root.get("trangThai")),
                    criteriaBuilder.greaterThan(root.get("ngayBatDau"), today)
            ));
            case "ENDED" -> specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.isTrue(root.get("trangThai")),
                    criteriaBuilder.lessThan(root.get("ngayKetThuc"), today)
            ));
            case "DISABLED" -> specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.isFalse(root.get("trangThai")));
            default -> {
                // ALL hoặc giá trị không xác định: không lọc trạng thái.
            }
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("maKhuyenMai"))
        );
        return promotionRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public List<Promotion> findActiveNow() {
        LocalDate today = LocalDate.now();
        return promotionRepository
                .findByTrangThaiTrueAndNgayBatDauLessThanEqualAndNgayKetThucGreaterThanEqual(today, today)
                .stream()
                .filter(this::hasRemainingUsage)
                .toList();
    }

    public Promotion findById(Integer id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khuyến mãi: " + id
                ));
    }

    @Transactional
    public Promotion create(PromotionRequest request) {
        validateRequest(request, null);
        String normalizedCode = normalizeCode(request.maCode());
        promotionRepository.findByMaCodeIgnoreCase(normalizedCode).ifPresent(existing -> {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mã khuyến mãi đã tồn tại: " + normalizedCode
            );
        });

        Promotion promotion = new Promotion();
        fillPromotion(promotion, request, normalizedCode);
        promotion.setSoLuotDaDung(0);
        Promotion savedPromotion = promotionRepository.save(promotion);

        systemActivityService.record(
                "PROMOTION_CREATED",
                "Khuyến mãi " + savedPromotion.getTenKhuyenMai() + " đã được tạo",
                savedPromotion.getMaKhuyenMai()
        );
        realtimeNotificationService.notifyDashboardRefresh(savedPromotion);
        return savedPromotion;
    }

    @Transactional
    public Promotion update(Integer id, PromotionRequest request) {
        Promotion promotion = findById(id);
        validateRequest(request, promotion);
        String normalizedCode = normalizeCode(request.maCode());

        promotionRepository.findByMaCodeIgnoreCase(normalizedCode).ifPresent(existing -> {
            if (!existing.getMaKhuyenMai().equals(id)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Mã khuyến mãi đã tồn tại: " + normalizedCode
                );
            }
        });

        int usedCount = usageCount(promotion);
        if (usedCount > 0 && financialRulesChanged(promotion, request, normalizedCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khuyến mãi đã được áp dụng cho đơn hàng; không thể sửa mã hoặc quy tắc giảm giá. "
                            + "Bạn có thể đổi tên, thời gian, trạng thái hoặc tăng giới hạn sử dụng."
            );
        }

        fillPromotion(promotion, request, normalizedCode);
        Promotion savedPromotion = promotionRepository.save(promotion);
        systemActivityService.record(
                "PROMOTION_UPDATED",
                "Khuyến mãi " + savedPromotion.getTenKhuyenMai() + " đã được cập nhật",
                savedPromotion.getMaKhuyenMai()
        );
        realtimeNotificationService.notifyDashboardRefresh(savedPromotion);
        return savedPromotion;
    }

    @Transactional
    public void delete(Integer id) {
        Promotion promotion = findById(id);
        promotion.setTrangThai(false);
        Promotion savedPromotion = promotionRepository.save(promotion);
        systemActivityService.record(
                "PROMOTION_DISABLED",
                "Khuyến mãi " + savedPromotion.getTenKhuyenMai() + " đã được tắt",
                savedPromotion.getMaKhuyenMai()
        );
        realtimeNotificationService.notifyDashboardRefresh(savedPromotion);
    }

    @Transactional
    public Order applyToOrder(ApplyPromotionRequest request) {
        return applyToOrder(request.maDonHang(), request.maCode());
    }

    /**
     * Giữ và áp dụng mã cho một đơn mới ngay trong transaction tạo đơn.
     * Dùng cho checkout giao hàng để backend kiểm tra khuyến mãi lần cuối
     * trước khi đơn được chuyển xuống bếp hoặc mở bước thanh toán VietQR.
     */
    @Transactional
        /**
     * Áp dụng hoặc thay thế mã khuyến mãi cho đơn hàng. Việc khóa cả đơn và mã
     * giúp hai request đồng thời không thể trừ tiền hoặc vượt lượt sử dụng.
     */
    @Transactional
    public Order applyToOrder(Integer orderId, String rawCode) {
        Order order = findOrderForUpdate(orderId);
        validateOrderCanChangePromotion(order);
        String code = normalizeCode(rawCode);

        Promotion currentPromotion = order.getKhuyenMai();
        if (currentPromotion != null && code.equalsIgnoreCase(currentPromotion.getMaCode())) {
            // Request lặp lại: chỉ tính lại, tuyệt đối không trừ thêm lần nữa.
            orderPricingService.recalculate(order);
            return orderRepository.saveAndFlush(order);
        }

        Promotion newPromotion = promotionRepository.findActiveByCodeForUpdate(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mã khuyến mãi không hợp lệ hoặc đã tắt"
                ));

        BigDecimal subtotal = orderPricingService.calculateSubtotal(order);
        validatePromotionCanBeApplied(newPromotion, subtotal);

        if (currentPromotion != null) {
            Promotion lockedCurrent = promotionRepository.findByIdForUpdate(currentPromotion.getMaKhuyenMai())
                    .orElse(currentPromotion);
            decrementUsage(lockedCurrent);
            promotionRepository.save(lockedCurrent);
        }

        incrementUsage(newPromotion);
        promotionRepository.save(newPromotion);

        order.setKhuyenMai(newPromotion);
        order.setThoiGianApDungKhuyenMai(LocalDateTime.now());
        orderPricingService.recalculate(order);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "PROMOTION_APPLIED",
                "Mã " + newPromotion.getMaCode() + " đã được áp dụng cho đơn #DH"
                        + savedOrder.getMaDonHang() + ", giảm " + savedOrder.getTienGiam(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyOrderPricingChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /** Gỡ mã và khôi phục tổng thanh toán bằng tạm tính. */
    @Transactional
    public Order removeFromOrder(Integer orderId) {
        Order order = findOrderForUpdate(orderId);
        validateOrderCanChangePromotion(order);

        Promotion currentPromotion = order.getKhuyenMai();
        if (currentPromotion == null) {
            orderPricingService.recalculate(order);
            return orderRepository.saveAndFlush(order);
        }

        Promotion lockedCurrent = promotionRepository.findByIdForUpdate(currentPromotion.getMaKhuyenMai())
                .orElse(currentPromotion);
        decrementUsage(lockedCurrent);
        promotionRepository.save(lockedCurrent);

        String removedCode = currentPromotion.getMaCode();
        clearPromotion(order);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "PROMOTION_REMOVED",
                "Mã " + removedCode + " đã được gỡ khỏi đơn #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyOrderPricingChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /**
     * Trả lại lượt đã giữ khi đơn bị hủy. OrderService sẽ lưu đơn và phát sự kiện
     * trạng thái trong cùng transaction.
     */
    @Transactional
    public void releaseForCancelledOrder(Order order) {
        if (order == null || order.getKhuyenMai() == null) {
            return;
        }
        Promotion current = order.getKhuyenMai();
        Promotion lockedCurrent = promotionRepository.findByIdForUpdate(current.getMaKhuyenMai())
                .orElse(current);
        decrementUsage(lockedCurrent);
        promotionRepository.save(lockedCurrent);
        clearPromotion(order);
    }

    private void fillPromotion(Promotion promotion,
                               PromotionRequest request,
                               String normalizedCode) {
        promotion.setMaCode(normalizedCode);
        promotion.setTenKhuyenMai(request.tenKhuyenMai().trim());
        promotion.setMoTa(trimToNull(request.moTa()));
        promotion.setLoaiGiam(orderPricingService.normalizeType(request.loaiGiam()));
        promotion.setGiaTriGiam(money(request.giaTriGiam()));
        promotion.setGiaTriDonToiThieu(money(defaultMoney(request.giaTriDonToiThieu())));
        promotion.setGiamToiDa(request.giamToiDa() == null ? null : money(request.giamToiDa()));
        promotion.setGioiHanSuDung(request.gioiHanSuDung());
        promotion.setNgayBatDau(request.ngayBatDau());
        promotion.setNgayKetThuc(request.ngayKetThuc());
        promotion.setTrangThai(request.trangThai() == null || request.trangThai());
    }

    private void validateRequest(PromotionRequest request, Promotion existing) {
        if (request.ngayKetThuc().isBefore(request.ngayBatDau())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu"
            );
        }

        String normalizedType = orderPricingService.normalizeType(request.loaiGiam());
        if (!Set.of("PERCENT", "FIXED").contains(normalizedType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loại giảm chỉ hỗ trợ PERCENT hoặc FIXED"
            );
        }
        if ("PERCENT".equals(normalizedType)
                && request.giaTriGiam().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Giá trị giảm theo phần trăm không được vượt quá 100"
            );
        }

        int usedCount = existing == null ? 0 : usageCount(existing);
        if (request.gioiHanSuDung() != null && request.gioiHanSuDung() < usedCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Giới hạn sử dụng không được nhỏ hơn số lượt đã áp dụng: " + usedCount
            );
        }
        if (normalizeCode(request.maCode()).length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi tối đa 50 ký tự");
        }
        if (request.tenKhuyenMai().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên khuyến mãi tối đa 100 ký tự");
        }
    }

    private void validatePromotionCanBeApplied(Promotion promotion, BigDecimal subtotal) {
        LocalDate today = LocalDate.now();
        if (!Boolean.TRUE.equals(promotion.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi đã bị tắt");
        }
        if (today.isBefore(promotion.getNgayBatDau())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi chưa đến thời gian áp dụng");
        }
        if (today.isAfter(promotion.getNgayKetThuc())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi đã hết hạn");
        }
        if (!hasRemainingUsage(promotion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã khuyến mãi đã hết lượt sử dụng");
        }
        if (!orderPricingService.isMinimumSatisfied(subtotal, promotion)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đơn hàng phải đạt tối thiểu " + money(promotion.getGiaTriDonToiThieu())
                            + " để áp dụng mã này"
            );
        }
        BigDecimal calculatedDiscount = orderPricingService.calculateDiscount(subtotal, promotion);
        if (subtotal.subtract(calculatedDiscount).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã khuyến mãi làm tổng thanh toán bằng 0; hệ thống hiện chưa hỗ trợ đơn miễn phí"
            );
        }
    }

    private void validateOrderCanChangePromotion(Order order) {
        String status = normalizeStatus(order.getTrangThai());
        if (!PROMOTION_EDITABLE_ORDER_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể áp dụng hoặc gỡ khuyến mãi khi đơn đã phục vụ hoặc đang chờ thanh toán"
            );
        }
    }

    private Order findOrderForUpdate(Integer orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đơn hàng không hợp lệ");
        }
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));
    }

    private void clearPromotion(Order order) {
        order.setKhuyenMai(null);
        order.setThoiGianApDungKhuyenMai(null);
        orderPricingService.recalculate(order);
    }

    private void incrementUsage(Promotion promotion) {
        int used = usageCount(promotion);
        Integer limit = promotion.getGioiHanSuDung();
        if (limit != null && used >= limit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã khuyến mãi đã hết lượt sử dụng");
        }
        promotion.setSoLuotDaDung(used + 1);
    }

    private void decrementUsage(Promotion promotion) {
        promotion.setSoLuotDaDung(Math.max(usageCount(promotion) - 1, 0));
    }

    private boolean hasRemainingUsage(Promotion promotion) {
        Integer limit = promotion.getGioiHanSuDung();
        return limit == null || usageCount(promotion) < limit;
    }

    private int usageCount(Promotion promotion) {
        return promotion.getSoLuotDaDung() == null ? 0 : Math.max(promotion.getSoLuotDaDung(), 0);
    }

    private boolean financialRulesChanged(Promotion promotion,
                                          PromotionRequest request,
                                          String normalizedCode) {
        return !normalizedCode.equalsIgnoreCase(promotion.getMaCode())
                || !orderPricingService.normalizeType(request.loaiGiam())
                .equals(orderPricingService.normalizeType(promotion.getLoaiGiam()))
                || money(request.giaTriGiam()).compareTo(money(promotion.getGiaTriGiam())) != 0
                || money(defaultMoney(request.giaTriDonToiThieu()))
                .compareTo(money(defaultMoney(promotion.getGiaTriDonToiThieu()))) != 0
                || !sameMoney(request.giamToiDa(), promotion.getGiamToiDa());
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return money(left).compareTo(money(right)) == 0;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập mã khuyến mãi");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
