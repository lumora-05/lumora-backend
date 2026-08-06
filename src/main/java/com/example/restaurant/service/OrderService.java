package com.example.restaurant.service;

import com.example.restaurant.dto.OrderCreateRequest;
import com.example.restaurant.dto.OrderItemCancellationDecisionRequest;
import com.example.restaurant.dto.OrderItemCancellationRequest;
import com.example.restaurant.dto.OrderItemCancellationResponse;
import com.example.restaurant.dto.OrderItemStatusUpdateRequest;
import com.example.restaurant.dto.OrderStatusUpdateRequest;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Food;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.FoodRepository;
import com.example.restaurant.repository.OrderItemRepository;
import com.example.restaurant.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.persistence.criteria.JoinType;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OrderService {
    private static final Set<String> OPEN_ORDER_STATUSES = Set.of(
            "CHO_XAC_NHAN",
            "DA_XAC_NHAN",
            "DANG_CHUAN_BI",
            "DANG_CHE_BIEN",
            "SAN_SANG",
            "SAN_SANG_PHUC_VU",
            "DA_HOAN_THANH",
            "DA_PHUC_VU",
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN"
    );

    private static final Set<String> VALID_ORDER_STATUSES = Set.of(
            "CHO_XAC_NHAN",
            "DA_XAC_NHAN",
            "DANG_CHUAN_BI",
            "DANG_CHE_BIEN",
            "SAN_SANG",
            "SAN_SANG_PHUC_VU",
            "DA_HOAN_THANH",
            "DA_PHUC_VU",
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN",
            "CHO_BAN_GIAO",
            "DANG_GIAO",
            "GIAO_THAT_BAI",
            "DA_THANH_TOAN",
            "DA_HUY"
    );

    private static final Set<String> PAYMENT_PENDING_STATUSES = Set.of(
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN"
    );

    /**
     * Admin chỉ được hủy đơn khi quy trình phục vụ chưa hoàn tất. Những thay đổi
     * trạng thái nghiệp vụ khác phải do đúng vai trò phục vụ, bếp hoặc thu ngân
     * thực hiện qua endpoint chuyên biệt.
     */
    private static final Set<String> ADMIN_CANCELLABLE_ORDER_STATUSES = Set.of(
            "CHO_XAC_NHAN",
            "DA_XAC_NHAN",
            "DANG_CHUAN_BI",
            "DANG_CHE_BIEN",
            "SAN_SANG",
            "SAN_SANG_PHUC_VU",
            "DA_HOAN_THANH"
    );

    private static final Set<String> VALID_ITEM_STATUSES = Set.of(
            "CHO_BEP",
            "DANG_NAU",
            "DANG_CHE_BIEN",
            "HOAN_THANH",
            "DA_HOAN_THANH",
            "DA_PHUC_VU"
    );

    private static final String ITEM_CANCELLATION_PENDING = "CHO_DUYET";
    private static final String ITEM_CANCELLATION_APPROVED = "DA_DUYET";
    private static final String ITEM_CANCELLATION_REJECTED = "TU_CHOI";
    private static final String ITEM_CANCELLATION_HOLD_STATUS = "YEU_CAU_HUY";

    private static final Map<String, String> ITEM_CANCELLATION_REASONS = Map.ofEntries(
            Map.entry("KHACH_DOI_Y", "Khách đổi ý"),
            Map.entry("KHACH_GOI_NHAM", "Khách gọi nhầm món"),
            Map.entry("NHAN_VIEN_NHAP_NHAM", "Nhân viên nhập nhầm"),
            Map.entry("KHACH_CHO_QUA_LAU", "Khách chờ quá lâu"),
            Map.entry("KHACH_DOI_MON", "Khách yêu cầu đổi món"),
            Map.entry("HET_NGUYEN_LIEU", "Món hết nguyên liệu"),
            Map.entry("BEP_KHONG_THE_CHE_BIEN", "Bếp không thể chế biến"),
            Map.entry("MON_KHONG_DUNG_YEU_CAU", "Món không đúng yêu cầu"),
            Map.entry("LY_DO_KHAC", "Lý do khác")
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DiningTableRepository diningTableRepository;
    private final FoodRepository foodRepository;
    private final EmployeeRepository employeeRepository;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;
    private final OrderPricingService orderPricingService;
    private final PromotionService promotionService;
    private final TableArrangementService tableArrangementService;
    private final ReservationService reservationService;
    private final FoodTraceabilityService foodTraceabilityService;
    private final DeliveryOrderService deliveryOrderService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        DiningTableRepository diningTableRepository,
                        FoodRepository foodRepository,
                        EmployeeRepository employeeRepository,
                        RealtimeNotificationService realtimeNotificationService,
                        SystemActivityService systemActivityService,
                        OrderPricingService orderPricingService,
                        PromotionService promotionService,
                        TableArrangementService tableArrangementService,
                        ReservationService reservationService,
                        FoodTraceabilityService foodTraceabilityService,
                        DeliveryOrderService deliveryOrderService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.diningTableRepository = diningTableRepository;
        this.foodRepository = foodRepository;
        this.employeeRepository = employeeRepository;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
        this.orderPricingService = orderPricingService;
        this.promotionService = promotionService;
        this.tableArrangementService = tableArrangementService;
        this.reservationService = reservationService;
        this.foodTraceabilityService = foodTraceabilityService;
        this.deliveryOrderService = deliveryOrderService;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Order> findAllForWaiter(String username) {
        Employee waiter = resolveActiveWaiterByUsername(username);
        if (!StringUtils.hasText(waiter.getKhuVucPhuTrach())) {
            return List.of();
        }
        return orderRepository.findByBanAn_KhuVucIgnoreCaseOrderByThoiGianDatDescMaDonHangDesc(
                waiter.getKhuVucPhuTrach().trim()
        );
    }

    @Transactional(readOnly = true)
    public Page<Order> findPage(int page,
                                int size,
                                String keyword,
                                String status,
                                LocalDate from,
                                LocalDate to,
                                boolean kitchenOnly,
                                String waiterUsername) {
        Specification<Order> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (kitchenOnly) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.notEqual(criteriaBuilder.upper(root.get("trangThai")), "CHO_XAC_NHAN"));
        }

        if (StringUtils.hasText(waiterUsername)) {
            Employee waiter = resolveActiveWaiterByUsername(waiterUsername);
            if (!StringUtils.hasText(waiter.getKhuVucPhuTrach())) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.disjunction());
            } else {
                String assignedArea = waiter.getKhuVucPhuTrach().trim().toLowerCase(Locale.ROOT);
                specification = specification.and((root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(
                                        criteriaBuilder.coalesce(
                                                root.get("banAn").<String>get("khuVuc"),
                                                "Khu vực chung"
                                        )
                                ),
                                assignedArea
                        ));
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim();
            String pattern = "%" + normalizedKeyword.toLowerCase() + "%";
            Integer orderId = parseInteger(normalizedKeyword);
            specification = specification.and((root, query, criteriaBuilder) -> {
                var tableJoin = root.join("banAn", JoinType.LEFT);
                var deliveryJoin = root.join("giaoHang", JoinType.LEFT);
                var byTable = criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(tableJoin.<String>get("tenBan"), "")),
                        pattern
                );
                var byStatus = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("trangThai")),
                        pattern
                );
                var byRecipient = criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(deliveryJoin.<String>get("tenNguoiNhan"), "")),
                        pattern
                );
                var byShippingCode = criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(deliveryJoin.<String>get("maVanChuyen"), "")),
                        pattern
                );
                var byPhone = criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(deliveryJoin.<String>get("soDienThoaiNhan"), "")),
                        pattern
                );
                return orderId == null
                        ? criteriaBuilder.or(byTable, byStatus, byRecipient, byShippingCode, byPhone)
                        : criteriaBuilder.or(
                                criteriaBuilder.equal(root.get("maDonHang"), orderId),
                                byTable,
                                byStatus,
                                byRecipient,
                                byShippingCode,
                                byPhone
                        );
            });
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            String normalizedStatus = normalizeStatus(status);
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(criteriaBuilder.upper(root.get("trangThai")), normalizedStatus));
        }

        if (from != null) {
            LocalDateTime fromTime = from.atStartOfDay();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("thoiGianDat"), fromTime));
        }
        if (to != null) {
            LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThan(root.get("thoiGianDat"), toExclusive));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("thoiGianDat"), Sort.Order.desc("maDonHang"))
        );
        return orderRepository.findAll(specification, pageable);
    }

    /**
     * Danh sách dành cho bếp: ẩn đơn chưa được phục vụ xác nhận.
     * Vẫn giữ các đơn đã kết thúc để trang lịch sử bếp có thể sử dụng.
     */
    public List<Order> findAllForKitchen() {
        return orderRepository.findAll().stream()
                .filter(order -> !"CHO_XAC_NHAN".equals(normalizeStatus(order.getTrangThai())))
                .toList();
    }

    public Order findByIdForKitchen(Integer id) {
        Order order = findById(id);
        if ("CHO_XAC_NHAN".equals(normalizeStatus(order.getTrangThai()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Đơn hàng chưa được nhân viên phục vụ xác nhận"
            );
        }
        return order;
    }

    public List<Order> findByStatusForKitchen(String status) {
        String normalized = normalizeStatus(status);
        if ("CHO_XAC_NHAN".equals(normalized)) {
            return List.of();
        }
        return orderRepository.findByTrangThai(normalized);
    }

    public List<Order> findByStatus(String status) {
        return orderRepository.findByTrangThai(normalizeStatus(status));
    }

    @Transactional(readOnly = true)
    public List<Order> findByStatusForWaiter(String status, String username) {
        Employee waiter = resolveActiveWaiterByUsername(username);
        if (!StringUtils.hasText(waiter.getKhuVucPhuTrach())) {
            return List.of();
        }
        return orderRepository.findByTrangThaiAndBanAn_KhuVucIgnoreCaseOrderByThoiGianDatDescMaDonHangDesc(
                normalizeStatus(status),
                waiter.getKhuVucPhuTrach().trim()
        );
    }

    @Transactional(readOnly = true)
    public List<OrderItem> findKitchenItems(String status) {
        return orderItemRepository.findByTrangThaiMon(normalizeStatus(status)).stream()
                .filter(item -> item.getDonHang() != null)
                .filter(item -> !"CHO_XAC_NHAN".equals(normalizeStatus(item.getDonHang().getTrangThai())))
                .toList();
    }

    public Order findById(Integer id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng: " + id));
    }

    /** API khách tại bàn không được đọc hoặc thao tác đơn giao hàng bằng mã tăng dần. */
    @Transactional(readOnly = true)
    public Order findTableOrderForCustomer(Integer id) {
        Order order = findById(id);
        if (order.isDeliveryOrder()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng tại bàn");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public Order findByIdForWaiter(Integer id, String username) {
        Order order = findById(id);
        ensureWaiterCanAccessOrder(resolveActiveWaiterByUsername(username), order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> findOpenOrdersByTable(Integer tableId) {
        Integer effectiveTableId = tableArrangementService.resolvePrimaryTableId(tableId);
        return orderRepository.findByBanAn_MaBanAndTrangThaiInOrderByThoiGianDatDescMaDonHangDesc(
                effectiveTableId,
                OPEN_ORDER_STATUSES
        );
    }

    @Transactional(readOnly = true)
    public Order findCurrentOrderByTable(Integer tableId) {
        Integer effectiveTableId = tableArrangementService.resolvePrimaryTableId(tableId);
        return orderRepository.findOpenOrders(effectiveTableId, OPEN_ORDER_STATUSES, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Bàn chưa có đơn hàng đang phục vụ: " + tableId
                ));
    }

    /**
     * Khách tạo đơn: hệ thống xác nhận tự động, chuyển món trực tiếp vào bếp
     * và đồng thời thông báo cho nhân viên phục vụ theo dõi.
     */
    @Transactional
    public Order createCustomerOrder(OrderCreateRequest request) {
        return createOrderInternal(request, null, false);
    }

    /**
     * Nhân viên phục vụ tạo đơn: backend tự lấy nhân viên từ JWT, xác nhận đơn ngay
     * và gửi trực tiếp các món mới vào bếp.
     */
    @Transactional
    public Order createOrderByStaff(OrderCreateRequest request, String username) {
        Employee employee = resolveActiveWaiterByUsername(username);
        return createOrderInternal(request, employee, true);
    }

    /**
     * Tạo đơn lần đầu hoặc thêm một lượt gọi món vào đơn đang mở của bàn.
     * Bản ghi bàn được khóa bi quan để hai request đồng thời không tạo hai đơn khác nhau.
     */
    private Order createOrderInternal(OrderCreateRequest request,
                                      Employee actingEmployee,
                                      boolean createdByStaff) {
        DiningTable table = createdByStaff
                ? resolveStaffTableForOrder(request.maBan())
                : resolveCustomerTableForOrder(request);
        if (createdByStaff) {
            ensureWaiterCanAccessTable(actingEmployee, table);
        }
        validateTableCanOrder(table);

        Order order = orderRepository
                .findOpenOrdersForUpdate(table.getMaBan(), OPEN_ORDER_STATUSES, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);

        boolean newOrder = order == null;
        String previousStatus = newOrder ? "" : normalizeStatus(order.getTrangThai());
        if (newOrder) {
            // Khách vãng lai/đơn mới không được chiếm bàn đã giữ cho lịch đặt sắp tới.
            // Lịch đã check-in và xếp bàn có trạng thái DA_XEP_BAN nên vẫn tạo đơn bình thường.
            reservationService.ensureTableAvailableForNewService(table);
            reservationService.ensureNoPendingPreorderForAssignedReservation(table);
            order = new Order();
            order.setBanAn(table);
            order.setGhiChu(trimToNull(request.ghiChu()));
            // Đơn do khách quét QR hoặc phục vụ tạo đều được chuyển thẳng xuống bếp.
            // Giữ trạng thái DA_XAC_NHAN để tương thích với luồng bếp và frontend hiện tại.
            order.setTrangThai("DA_XAC_NHAN");
            order.setTamTinh(BigDecimal.ZERO);
            order.setTienGiam(BigDecimal.ZERO);
            order.setTongTien(BigDecimal.ZERO);
            if (createdByStaff) {
                order.setNhanVien(actingEmployee);
            }
        } else {
            if (PAYMENT_PENDING_STATUSES.contains(previousStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Đơn hàng đang chờ thanh toán. Vui lòng hoàn tất thanh toán trước khi gọi thêm món."
                );
            }
            order.setGhiChu(mergeNotes(order.getGhiChu(), request.ghiChu()));
            if (createdByStaff) {
                order.setNhanVien(actingEmployee);
            }
        }

        int callNumber = nextCallNumber(order);
        LocalDateTime addedAt = LocalDateTime.now();
        List<OrderItem> newItems = new ArrayList<>();

        for (OrderCreateRequest.Item reqItem : request.items()) {
            Food food = foodRepository.findById(reqItem.maMonAn())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy món ăn: " + reqItem.maMonAn()
                    ));
            if (!Boolean.TRUE.equals(food.getTrangThai())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Món ăn đang ngừng bán: " + food.getTenMonAn()
                );
            }

            // Mỗi suất ăn là một chi tiết đơn hàng riêng để bếp có thể
            // bắt đầu, hoàn thành hoặc hoàn tác từng suất độc lập.
            for (int unit = 0; unit < reqItem.soLuong(); unit++) {
                OrderItem item = new OrderItem();
                item.setMonAn(food);
                item.setSoLuong(1);
                item.setDonGia(food.getGia());
                item.setGhiChu(trimToNull(reqItem.ghiChu()));
                item.setTrangThaiMon("CHO_BEP");
                item.setLanGoi(callNumber);
                item.setThoiGianThem(addedAt);
                order.addItem(item);
                newItems.add(item);
            }

        }

        // Luôn tính lại từ chi tiết món để khuyến mãi không bị cộng/trừ lặp khi gọi thêm món.
        orderPricingService.recalculate(order);
        if (!newOrder) {
            if (createdByStaff && "CHO_XAC_NHAN".equals(previousStatus)) {
                if (hasPendingItemCancellation(order)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Vui lòng xử lý yêu cầu hủy món trước khi xác nhận đơn"
                    );
                }
                // Phục vụ nhập món cho đơn khách vừa gửi: xác nhận luôn và chuyển vào bếp.
                syncOrderTimestamps(order, previousStatus, "DA_XAC_NHAN");
                order.setTrangThai("DA_XAC_NHAN");
            } else if (!"CHO_XAC_NHAN".equals(previousStatus)) {
                // Tính lại trạng thái đơn theo trạng thái thực tế của từng món.
                // Món gọi thêm mới chỉ ở CHO_BEP nên không được chuyển đơn sang DANG_CHE_BIEN
                // trước khi bếp thực sự bắt đầu chế biến.
                synchronizeOrderStatusFromItems(order, true);
            }
        }

        tableArrangementService.updateServiceStatus(table, "DANG_SU_DUNG");
        Order savedOrder = orderRepository.saveAndFlush(order);
        if (newOrder) {
            reservationService.linkOrderToAssignedReservation(savedOrder);
        }

        if (newOrder) {
            systemActivityService.record(
                    createdByStaff ? "STAFF_ORDER_CREATED" : "NEW_ORDER",
                    "Đơn hàng #DH" + savedOrder.getMaDonHang() + " đã được tạo tại " + table.getTenBan()
                            + (createdByStaff ? " bởi nhân viên " + actingEmployee.getHoTen() : ""),
                    savedOrder.getMaDonHang()
            );
            // Phục vụ nhận thông báo để theo dõi, đồng thời bếp nhận đơn ngay lập tức.
            realtimeNotificationService.notifyNewOrder(savedOrder);
            realtimeNotificationService.notifyKitchenOrderConfirmed(savedOrder);
        } else {
            systemActivityService.record(
                    "ORDER_ITEMS_ADDED",
                    "Đơn hàng #DH" + savedOrder.getMaDonHang() + " đã gọi thêm lượt " + callNumber
                            + " với " + newItems.size() + " món",
                    savedOrder.getMaDonHang()
            );
            boolean confirmedForKitchen = !"CHO_XAC_NHAN".equals(normalizeStatus(savedOrder.getTrangThai()));
            realtimeNotificationService.notifyOrderItemsAdded(
                    savedOrder,
                    newItems,
                    callNumber,
                    confirmedForKitchen
            );
        }

        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Integer orderId,
                                   OrderStatusUpdateRequest request,
                                   String username,
                                   boolean admin) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));
        String oldStatus = normalizeStatus(order.getTrangThai());
        String newStatus = normalizeStatus(request.trangThai());
        Employee actingWaiter = null;

        if (order.isDeliveryOrder()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn giao hàng phải được xử lý qua API nghiệp vụ giao hàng"
            );
        }

        if (!admin) {
            actingWaiter = resolveActiveWaiterByUsername(username);
            ensureWaiterCanAccessOrder(actingWaiter, order);
        }

        if (!VALID_ORDER_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đơn hàng không hợp lệ: " + newStatus);
        }
        if ("DA_THANH_TOAN".equals(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể chuyển trực tiếp sang DA_THANH_TOAN. Hãy thanh toán qua API hóa đơn."
            );
        }
        if (Set.of("DA_THANH_TOAN", "DA_HUY").contains(oldStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã kết thúc, không thể đổi trạng thái");
        }

        if (oldStatus.equals(newStatus)) {
            // Cho phép request lặp lại an toàn, nhưng không ghi hoạt động giả.
            return order;
        }

        if (admin) {
            validateAdminOrderTransition(order, oldStatus, newStatus);
        } else {
            validateWaiterOrderTransition(oldStatus, newStatus);
            if ("DA_XAC_NHAN".equals(newStatus) && hasPendingItemCancellation(order)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Vui lòng xử lý yêu cầu hủy món trước khi xác nhận đơn"
                );
            }
            // Không tin mã nhân viên do frontend gửi; luôn lấy đúng tài khoản trong JWT.
            order.setNhanVien(actingWaiter);
        }

        if ("DA_HUY".equals(newStatus)) {
            promotionService.releaseForCancelledOrder(order);
        }

        order.setTrangThai(newStatus);
        syncOrderTimestamps(order, oldStatus, newStatus);
        syncTableStatusAfterOrderStatus(order, newStatus);
        Order savedOrder = orderRepository.saveAndFlush(order);
        if ("DA_HUY".equals(newStatus)) {
            reservationService.cancelByOrder(savedOrder);
        }

        systemActivityService.record(
                "ORDER_STATUS_CHANGED",
                "Đơn hàng #DH" + savedOrder.getMaDonHang() + " đã chuyển từ " + oldStatus + " sang " + newStatus,
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyOrderStatusChanged(savedOrder);
        if ("CHO_XAC_NHAN".equals(oldStatus) && "DA_XAC_NHAN".equals(newStatus)) {
            realtimeNotificationService.notifyKitchenOrderConfirmed(savedOrder);
        }
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /**
     * Endpoint nghiệp vụ riêng dành cho khách hàng yêu cầu thanh toán.
     * Chỉ chuyển đơn đã phục vụ sang chờ thanh toán, không nhận trạng thái tùy ý từ client.
     */
    @Transactional
    public Order requestPaymentByCustomer(Integer orderId) {
        Order order = findById(orderId);
        if (order.isDeliveryOrder()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn giao hàng được thanh toán theo quy trình giao tận nơi"
            );
        }
        String currentStatus = normalizeStatus(order.getTrangThai());

        // Cho phép gọi lặp lại an toàn khi request trước đã được xử lý thành công.
        if (PAYMENT_PENDING_STATUSES.contains(currentStatus)) {
            return order;
        }

        if (!"DA_PHUC_VU".equals(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể yêu cầu thanh toán sau khi đơn hàng đã được phục vụ"
            );
        }

        syncOrderTimestamps(order, currentStatus, "CHO_THANH_TOAN");
        order.setTrangThai("CHO_THANH_TOAN");
        syncTableStatusAfterOrderStatus(order, "CHO_THANH_TOAN");
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "CUSTOMER_PAYMENT_REQUESTED",
                "Khách tại " + savedOrder.getBanAn().getTenBan()
                        + " đã yêu cầu thanh toán đơn #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyOrderStatusChanged(savedOrder);
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /**
     * Nhân viên phục vụ ghi nhận yêu cầu thanh toán khi khách gọi trực tiếp.
     * Chỉ chuyển đơn đã phục vụ sang chờ thanh toán và cho phép gọi lặp lại an toàn.
     */
    @Transactional
    public Order requestPaymentByWaiter(Integer orderId, String username) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));
        if (order.isDeliveryOrder()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn giao hàng được thanh toán theo quy trình giao tận nơi"
            );
        }
        ensureWaiterCanAccessOrder(resolveActiveWaiterByUsername(username), order);
        String currentStatus = normalizeStatus(order.getTrangThai());

        if (PAYMENT_PENDING_STATUSES.contains(currentStatus)) {
            return order;
        }

        if (!"DA_PHUC_VU".equals(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể yêu cầu thanh toán sau khi đơn hàng đã được phục vụ"
            );
        }

        syncOrderTimestamps(order, currentStatus, "CHO_THANH_TOAN");
        order.setTrangThai("CHO_THANH_TOAN");
        syncTableStatusAfterOrderStatus(order, "CHO_THANH_TOAN");
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "WAITER_PAYMENT_REQUESTED",
                "Nhân viên phục vụ đã ghi nhận yêu cầu thanh toán tại "
                        + savedOrder.getBanAn().getTenBan()
                        + " cho đơn #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyOrderStatusChanged(savedOrder);
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /**
     * Phục vụ hoặc admin hủy món và bắt buộc chọn lý do.
     * Món CHO_BEP được hủy ngay. Món đang nấu do phục vụ yêu cầu sẽ chuyển sang
     * YEU_CAU_HUY để admin duyệt; admin có thể hủy trực tiếp món đang nấu.
     */
    @Transactional
    public OrderItem cancelOrRequestItemCancellation(Integer itemId,
                                                     OrderItemCancellationRequest request,
                                                     String username,
                                                     boolean admin) {
        Order order = findLockedOrderByItem(itemId);
        OrderItem item = findItemInOrder(itemId, order);
        Employee actor = resolveActiveEmployeeByUsername(username);
        if (!admin) {
            ensureWaiterRole(actor);
            ensureWaiterCanAccessOrder(actor, order);
        }

        validateOrderAllowsItemCancellation(order);
        String currentItemStatus = normalizeStatus(item.getTrangThaiMon());
        if ("DA_HUY".equals(currentItemStatus)) {
            return item;
        }
        if (ITEM_CANCELLATION_HOLD_STATUS.equals(currentItemStatus)
                || ITEM_CANCELLATION_PENDING.equals(normalizeStatus(item.getTrangThaiHuy()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đang có yêu cầu hủy chờ xử lý");
        }
        if (isCompletedItemStatus(currentItemStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món đã hoàn thành hoặc đã phục vụ, không thể hủy theo luồng thông thường"
            );
        }

        CancellationReason reason = resolveCancellationReason(request);
        String requestSource = admin ? "ADMIN" : "NHAN_VIEN_PHUC_VU";
        initializeCancellationRequest(item, currentItemStatus, reason, requestSource, actor);

        if ("CHO_BEP".equals(currentItemStatus) || (admin && isCookingItemStatus(currentItemStatus))) {
            completeItemCancellation(order, item, actor, null);
            OrderItem savedItem = orderItemRepository.save(item);
            Order savedOrder = orderRepository.saveAndFlush(order);
            publishItemCancellationCompleted(savedOrder, savedItem, actor.getHoTen());
            return savedItem;
        }

        if (isCookingItemStatus(currentItemStatus)) {
            item.setTrangThaiMon(ITEM_CANCELLATION_HOLD_STATUS);
            item.setTrangThaiHuy(ITEM_CANCELLATION_PENDING);
            deliveryOrderService.synchronizeAfterKitchenUpdate(order);
            OrderItem savedItem = orderItemRepository.save(item);
            Order savedOrder = orderRepository.saveAndFlush(order);
            publishItemCancellationRequested(savedOrder, savedItem, actor.getHoTen());
            return savedItem;
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Món ở trạng thái " + currentItemStatus + " không thể hủy"
        );
    }

    /**
     * Khách tại đúng bàn có thể gửi yêu cầu hủy món chưa bắt đầu chế biến.
     * Yêu cầu cần phục vụ của khu vực hoặc admin duyệt trước khi món chuyển DA_HUY.
     */
    @Transactional
    public OrderItem requestItemCancellationByCustomer(String qrToken,
                                                       Integer orderId,
                                                       Integer itemId,
                                                       OrderItemCancellationRequest request) {
        DiningTable scannedTable = resolveCustomerTableByQrToken(qrToken);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));
        Integer effectiveTableId = tableArrangementService.resolvePrimaryTableId(scannedTable.getMaBan());
        if (order.getBanAn() == null || !Objects.equals(order.getBanAn().getMaBan(), effectiveTableId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Đơn hàng không thuộc bàn của mã QR hiện tại"
            );
        }

        OrderItem item = findItemInOrder(itemId, order);
        validateOrderAllowsItemCancellation(order);
        String currentItemStatus = normalizeStatus(item.getTrangThaiMon());
        if ("DA_HUY".equals(currentItemStatus)) {
            return item;
        }
        if (ITEM_CANCELLATION_HOLD_STATUS.equals(currentItemStatus)
                || ITEM_CANCELLATION_PENDING.equals(normalizeStatus(item.getTrangThaiHuy()))) {
            return item;
        }
        if (!"CHO_BEP".equals(currentItemStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món đã được bếp tiếp nhận. Vui lòng gọi nhân viên phục vụ để được hỗ trợ"
            );
        }

        CancellationReason reason = resolveCancellationReason(request);
        initializeCancellationRequest(item, currentItemStatus, reason, "KHACH_HANG", null);
        item.setTrangThaiMon(ITEM_CANCELLATION_HOLD_STATUS);
        item.setTrangThaiHuy(ITEM_CANCELLATION_PENDING);
        OrderItem savedItem = orderItemRepository.save(item);
        Order savedOrder = orderRepository.saveAndFlush(order);
        publishItemCancellationRequested(savedOrder, savedItem, "Khách hàng");
        return savedItem;
    }

    /** Danh sách yêu cầu hủy món, phục vụ chỉ thấy các bàn thuộc khu vực được phân công. */
    @Transactional(readOnly = true)
    public List<OrderItemCancellationResponse> findItemCancellationRequests(String status,
                                                                            String username,
                                                                            boolean admin) {
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeStatus(status)
                : ITEM_CANCELLATION_PENDING;
        if (!Set.of(ITEM_CANCELLATION_PENDING, ITEM_CANCELLATION_APPROVED, ITEM_CANCELLATION_REJECTED)
                .contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái yêu cầu hủy không hợp lệ");
        }

        List<OrderItem> items = orderItemRepository
                .findByTrangThaiHuyOrderByThoiGianYeuCauHuyDesc(normalizedStatus);
        if (!admin) {
            Employee waiter = resolveActiveWaiterByUsername(username);
            items = items.stream()
                    .filter(item -> item.getDonHang() != null)
                    .filter(item -> waiterCanAccessOrder(waiter, item.getDonHang()))
                    .toList();
        }
        return items.stream().map(this::toCancellationResponse).toList();
    }

    /** Duyệt yêu cầu hủy. Phục vụ chỉ được duyệt món vốn đang CHO_BEP; món đang nấu do admin duyệt. */
    @Transactional
    public OrderItem approveItemCancellation(Integer itemId,
                                             OrderItemCancellationDecisionRequest request,
                                             String username,
                                             boolean admin) {
        Order order = findLockedOrderByItem(itemId);
        OrderItem item = findItemInOrder(itemId, order);
        Employee actor = resolveActiveEmployeeByUsername(username);
        if (!admin) {
            ensureWaiterRole(actor);
            ensureWaiterCanAccessOrder(actor, order);
        }
        validatePendingCancellation(item);

        String previousItemStatus = normalizeStatus(item.getTrangThaiTruocHuy());
        if (!admin && !"CHO_BEP".equals(previousItemStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Món đã bắt đầu chế biến, chỉ admin được duyệt hủy"
            );
        }
        if (isCompletedItemStatus(previousItemStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món đã hoàn thành hoặc đã phục vụ, không thể duyệt hủy"
            );
        }

        completeItemCancellation(order, item, actor, request == null ? null : request.ghiChu());
        OrderItem savedItem = orderItemRepository.save(item);
        Order savedOrder = orderRepository.saveAndFlush(order);
        publishItemCancellationCompleted(savedOrder, savedItem, actor.getHoTen());
        return savedItem;
    }

    /** Từ chối yêu cầu hủy và khôi phục trạng thái món trước khi gửi yêu cầu. */
    @Transactional
    public OrderItem rejectItemCancellation(Integer itemId,
                                            OrderItemCancellationDecisionRequest request,
                                            String username,
                                            boolean admin) {
        Order order = findLockedOrderByItem(itemId);
        OrderItem item = findItemInOrder(itemId, order);
        Employee actor = resolveActiveEmployeeByUsername(username);
        if (!admin) {
            ensureWaiterRole(actor);
            ensureWaiterCanAccessOrder(actor, order);
        }
        validatePendingCancellation(item);

        String previousItemStatus = normalizeStatus(item.getTrangThaiTruocHuy());
        if (!admin && !"CHO_BEP".equals(previousItemStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Yêu cầu hủy món đang chế biến chỉ admin được xử lý"
            );
        }
        if (!StringUtils.hasText(previousItemStatus)) {
            previousItemStatus = "CHO_BEP";
        }

        String oldOrderStatus = normalizeStatus(order.getTrangThai());
        item.setTrangThaiMon(previousItemStatus);
        item.setTrangThaiHuy(ITEM_CANCELLATION_REJECTED);
        item.setNguoiXuLyHuy(actor);
        item.setThoiGianXuLyHuy(LocalDateTime.now());
        item.setGhiChuXuLyHuy(trimToNull(request == null ? null : request.ghiChu()));
        synchronizeOrderStatusFromItems(order);
        deliveryOrderService.synchronizeAfterKitchenUpdate(order);

        OrderItem savedItem = orderItemRepository.save(item);
        Order savedOrder = orderRepository.saveAndFlush(order);
        systemActivityService.record(
                "ORDER_ITEM_CANCELLATION_REJECTED",
                "Yêu cầu hủy món " + item.getMonAn().getTenMonAn() + " trong đơn #DH"
                        + savedOrder.getMaDonHang() + " đã bị từ chối bởi " + actor.getHoTen(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyItemCancellationRejected(savedItem);
        if (!Objects.equals(oldOrderStatus, normalizeStatus(savedOrder.getTrangThai()))) {
            realtimeNotificationService.notifyOrderStatusChanged(savedOrder);
        }
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedItem;
    }

    /**
     * Nhân viên phục vụ xác nhận một món đã được mang ra bàn.
     * Chỉ cho phép món đã hoàn thành chuyển sang DA_PHUC_VU. Khi toàn bộ món
     * đều đã phục vụ, trạng thái đơn được tự động chuyển sang DA_PHUC_VU.
     */
    @Transactional
    public OrderItem markItemServed(Integer itemId, String username) {
        // Khóa đơn trước khi đọc chi tiết món để nhiều request phục vụ cùng một đơn
        // không bị tranh chấp và bỏ sót bước chuyển toàn đơn sang DA_PHUC_VU.
        Order order = orderRepository.findOrderByItemIdForUpdate(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết đơn hàng: " + itemId
                ));
        if (order.isDeliveryOrder()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn giao hàng không có bước phục vụ tại bàn"
            );
        }
        ensureWaiterCanAccessOrder(resolveActiveWaiterByUsername(username), order);

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết đơn hàng: " + itemId
                ));

        String orderStatus = normalizeStatus(order.getTrangThai());
        if ("CHO_XAC_NHAN".equals(orderStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng chưa được nhân viên phục vụ xác nhận"
            );
        }
        if (Set.of("CHO_THANH_TOAN", "SAN_SANG_THANH_TOAN", "DA_THANH_TOAN", "DA_HUY")
                .contains(orderStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng không còn ở trạng thái có thể xác nhận phục vụ món"
            );
        }

        String currentItemStatus = normalizeStatus(item.getTrangThaiMon());

        // Cho phép gọi lặp lại an toàn khi request trước đã thành công.
        if ("DA_PHUC_VU".equals(currentItemStatus)) {
            return item;
        }

        if (!("HOAN_THANH".equals(currentItemStatus)
                || "DA_HOAN_THANH".equals(currentItemStatus)
                || "SAN_SANG".equals(currentItemStatus)
                || "SAN_SANG_PHUC_VU".equals(currentItemStatus))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể xác nhận phục vụ khi món đã hoàn thành"
            );
        }

        String oldOrderStatus = normalizeStatus(order.getTrangThai());
        item.setTrangThaiMon("DA_PHUC_VU");
        OrderItem savedItem = orderItemRepository.save(item);

        List<OrderItem> activeItems = order.getChiTietDonHang() == null
                ? List.of()
                : order.getChiTietDonHang().stream()
                .filter(orderItem -> !"DA_HUY".equals(normalizeStatus(orderItem.getTrangThaiMon())))
                .toList();
        boolean hasPendingCancellation = activeItems.stream()
                .anyMatch(orderItem -> ITEM_CANCELLATION_HOLD_STATUS.equals(
                        normalizeStatus(orderItem.getTrangThaiMon())
                ));
        boolean allServed = !activeItems.isEmpty()
                && !hasPendingCancellation
                && activeItems.stream()
                .allMatch(orderItem -> "DA_PHUC_VU".equals(normalizeStatus(orderItem.getTrangThaiMon())));

        if (allServed) {
            syncOrderTimestamps(order, oldOrderStatus, "DA_PHUC_VU");
            order.setTrangThai("DA_PHUC_VU");
            syncTableStatusAfterOrderStatus(order, "DA_PHUC_VU");
        } else {
            // Nếu món vừa phục vụ là món sẵn sàng cuối cùng nhưng vẫn còn món đang nấu,
            // đưa đơn về DANG_CHE_BIEN để danh sách phục vụ không báo sai.
            synchronizeOrderStatusFromItems(order);
        }

        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "WAITER_ITEM_SERVED",
                "Món " + savedItem.getMonAn().getTenMonAn() + " trong đơn #DH"
                        + savedOrder.getMaDonHang() + " đã được phục vụ",
                savedOrder.getMaDonHang()
        );

        realtimeNotificationService.notifyOrderItemServed(savedItem);
        if (!Objects.equals(oldOrderStatus, savedOrder.getTrangThai())) {
            realtimeNotificationService.notifyOrderStatusChanged(savedOrder);
        }
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedItem;
    }

    @Transactional
    public OrderItem updateItemStatus(Integer itemId,
                                      OrderItemStatusUpdateRequest request,
                                      String username) {
        // Khóa đơn chứa món để các thao tác bếp đồng thời không cấp phát nguyên liệu hai lần.
        Order order = findLockedOrderByItem(itemId);
        OrderItem item = findItemInOrder(itemId, order);
        String orderStatus = normalizeStatus(order.getTrangThai());
        if ("CHO_XAC_NHAN".equals(orderStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng chưa được nhân viên phục vụ xác nhận"
            );
        }
        if (Set.of("DANG_GIAO", "GIAO_THAT_BAI", "DA_THANH_TOAN", "DA_HUY").contains(orderStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã bàn giao hoặc kết thúc, không thể cập nhật món");
        }

        String oldItemStatus = normalizeStatus(item.getTrangThaiMon());
        String newItemStatus = normalizeStatus(request.trangThaiMon());
        if (!VALID_ITEM_STATUSES.contains(newItemStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái món không hợp lệ: " + newItemStatus);
        }
        if (ITEM_CANCELLATION_HOLD_STATUS.equals(oldItemStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món đang chờ xử lý yêu cầu hủy, không thể cập nhật trạng thái chế biến"
            );
        }
        validateItemTransition(oldItemStatus, newItemStatus);

        // Chỉ cấp phát ở lần đầu món đi từ chờ bếp sang chế biến/hoàn thành.
        // Món chưa thiết lập công thức tiếp tục hoạt động như trước để bảo toàn dữ liệu cũ.
        boolean startsCooking = !isCookingItemStatus(oldItemStatus)
                && !isReadyForServiceStatus(oldItemStatus)
                && (isCookingItemStatus(newItemStatus) || isReadyForServiceStatus(newItemStatus));
        if (startsCooking) {
            foodTraceabilityService.allocateForCooking(item, username);
        }

        String oldOrderStatus = order.getTrangThai();
        item.setTrangThaiMon(newItemStatus);
        OrderItem savedItem = orderItemRepository.save(item);
        synchronizeOrderStatusFromItems(order);
        deliveryOrderService.synchronizeAfterKitchenUpdate(order);
        orderPricingService.recalculate(order);
        orderRepository.saveAndFlush(order);

        Integer orderId = order.getMaDonHang();
        systemActivityService.record(
                "KITCHEN_ITEM_STATUS_CHANGED",
                "Món " + savedItem.getMonAn().getTenMonAn() + " trong đơn #DH" + orderId
                        + " đã chuyển sang " + savedItem.getTrangThaiMon(),
                orderId
        );
        realtimeNotificationService.notifyKitchenItemStatusChanged(savedItem);
        if (!Objects.equals(oldOrderStatus, order.getTrangThai())) {
            realtimeNotificationService.notifyOrderStatusChanged(order);
        }
        realtimeNotificationService.notifyCustomerOrderChanged(order);
        realtimeNotificationService.notifyDashboardRefresh(order);
        return savedItem;
    }

    private Order findLockedOrderByItem(Integer itemId) {
        return orderRepository.findOrderByItemIdForUpdate(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết đơn hàng: " + itemId
                ));
    }

    private OrderItem findItemInOrder(Integer itemId, Order order) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết đơn hàng: " + itemId
                ));
        if (item.getDonHang() == null
                || order == null
                || !Objects.equals(item.getDonHang().getMaDonHang(), order.getMaDonHang())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món không thuộc đơn hàng đang được xử lý"
            );
        }
        return item;
    }

    private void validateOrderAllowsItemCancellation(Order order) {
        if (order != null && order.isDeliveryOrder()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn giao hàng chỉ được hủy toàn bộ khi còn chờ xác nhận; không hủy lẻ món sau khi chuyển xuống bếp"
            );
        }
        String orderStatus = normalizeStatus(order.getTrangThai());
        if (Set.of(
                "DA_PHUC_VU",
                "CHO_THANH_TOAN",
                "SAN_SANG_THANH_TOAN",
                "DA_THANH_TOAN",
                "DA_HUY"
        ).contains(orderStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng ở trạng thái " + orderStatus + ", không thể hủy món"
            );
        }
    }

    private CancellationReason resolveCancellationReason(OrderItemCancellationRequest request) {
        if (request == null || !StringUtils.hasText(request.maLyDo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lý do hủy món");
        }
        String code = normalizeStatus(request.maLyDo());
        String label = ITEM_CANCELLATION_REASONS.get(code);
        if (label == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã lý do hủy không hợp lệ: " + code
            );
        }
        String note = trimToNull(request.ghiChu());
        if ("LY_DO_KHAC".equals(code) && note == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập nội dung khi chọn lý do khác"
            );
        }
        return new CancellationReason(code, label, note);
    }

    private void initializeCancellationRequest(OrderItem item,
                                               String previousItemStatus,
                                               CancellationReason reason,
                                               String source,
                                               Employee requester) {
        item.setTrangThaiTruocHuy(previousItemStatus);
        item.setMaLyDoHuy(reason.code());
        item.setLyDoHuy(reason.label());
        item.setGhiChuHuy(reason.note());
        item.setNguonYeuCauHuy(source);
        item.setNguoiYeuCauHuy(requester);
        item.setThoiGianYeuCauHuy(LocalDateTime.now());
        item.setNguoiXuLyHuy(null);
        item.setThoiGianXuLyHuy(null);
        item.setGhiChuXuLyHuy(null);
    }

    private void completeItemCancellation(Order order,
                                          OrderItem item,
                                          Employee handler,
                                          String decisionNote) {
        String oldOrderStatus = normalizeStatus(order.getTrangThai());
        item.setTrangThaiMon("DA_HUY");
        foodTraceabilityService.markAllocatedUsageCancelled(item.getMaChiTiet());
        item.setTrangThaiHuy(ITEM_CANCELLATION_APPROVED);
        item.setNguoiXuLyHuy(handler);
        item.setThoiGianXuLyHuy(LocalDateTime.now());
        item.setGhiChuXuLyHuy(trimToNull(decisionNote));
        orderPricingService.recalculate(order);

        boolean hasRemainingItem = order.getChiTietDonHang() != null
                && order.getChiTietDonHang().stream()
                .anyMatch(orderItem -> !"DA_HUY".equals(normalizeStatus(orderItem.getTrangThaiMon())));
        if (!hasRemainingItem) {
            promotionService.releaseForCancelledOrder(order);
            syncOrderTimestamps(order, oldOrderStatus, "DA_HUY");
            order.setTrangThai("DA_HUY");
            syncTableStatusAfterOrderStatus(order, "DA_HUY");
        } else {
            synchronizeOrderStatusFromItems(order);
        }
        deliveryOrderService.synchronizeAfterKitchenUpdate(order);
    }

    private void validatePendingCancellation(OrderItem item) {
        if (!ITEM_CANCELLATION_PENDING.equals(normalizeStatus(item.getTrangThaiHuy()))
                || !ITEM_CANCELLATION_HOLD_STATUS.equals(normalizeStatus(item.getTrangThaiMon()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món không có yêu cầu hủy đang chờ xử lý"
            );
        }
    }

    private boolean hasPendingItemCancellation(Order order) {
        return order != null
                && order.getChiTietDonHang() != null
                && order.getChiTietDonHang().stream()
                .anyMatch(item -> ITEM_CANCELLATION_PENDING.equals(normalizeStatus(item.getTrangThaiHuy()))
                        || ITEM_CANCELLATION_HOLD_STATUS.equals(normalizeStatus(item.getTrangThaiMon())));
    }

    private void publishItemCancellationRequested(Order order, OrderItem item, String requesterName) {
        systemActivityService.record(
                "ORDER_ITEM_CANCELLATION_REQUESTED",
                requesterName + " đã yêu cầu hủy món " + item.getMonAn().getTenMonAn()
                        + " trong đơn #DH" + order.getMaDonHang()
                        + ". Lý do: " + item.getLyDoHuy(),
                order.getMaDonHang()
        );
        realtimeNotificationService.notifyItemCancellationRequested(item);
        realtimeNotificationService.notifyCustomerOrderChanged(order);
        realtimeNotificationService.notifyDashboardRefresh(order);
    }

    private void publishItemCancellationCompleted(Order order, OrderItem item, String handlerName) {
        if (order != null && "DA_HUY".equals(normalizeStatus(order.getTrangThai()))) {
            reservationService.cancelByOrder(order);
        }
        systemActivityService.record(
                "ORDER_ITEM_CANCELLED",
                "Món " + item.getMonAn().getTenMonAn() + " trong đơn #DH"
                        + order.getMaDonHang() + " đã được hủy bởi " + handlerName
                        + ". Lý do: " + item.getLyDoHuy(),
                order.getMaDonHang()
        );
        realtimeNotificationService.notifyItemCancellationCompleted(item);
        realtimeNotificationService.notifyOrderStatusChanged(order);
        realtimeNotificationService.notifyCustomerOrderChanged(order);
        realtimeNotificationService.notifyDashboardRefresh(order);
    }

    private DiningTable resolveCustomerTableByQrToken(String qrToken) {
        if (!StringUtils.hasText(qrToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã QR không được để trống");
        }
        DiningTable table = diningTableRepository.findByQrToken(qrToken.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã QR không hợp lệ"));
        if (!StringUtils.hasText(table.getQrToken())
                || !StringUtils.hasText(table.getAnhQr())
                || !"DANG_HOAT_DONG".equals(normalizeStatus(table.getTrangThaiQr()))) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Mã QR của bàn đang tạm ngưng hoặc không còn sử dụng"
            );
        }
        return table;
    }

    private void ensureWaiterRole(Employee employee) {
        String roleName = employee.getVaiTro() == null || employee.getVaiTro().getTenVaiTro() == null
                ? ""
                : employee.getVaiTro().getTenVaiTro().trim().toUpperCase(Locale.ROOT).replace("ROLE_", "");
        if (!"WAITER".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không phải nhân viên phục vụ");
        }
    }

    private boolean waiterCanAccessOrder(Employee waiter, Order order) {
        if (waiter == null
                || order == null
                || order.getBanAn() == null
                || !StringUtils.hasText(waiter.getKhuVucPhuTrach())) {
            return false;
        }
        String tableArea = StringUtils.hasText(order.getBanAn().getKhuVuc())
                ? order.getBanAn().getKhuVuc().trim()
                : "Khu vực chung";
        return waiter.getKhuVucPhuTrach().trim().equalsIgnoreCase(tableArea);
    }

    private OrderItemCancellationResponse toCancellationResponse(OrderItem item) {
        Order order = item.getDonHang();
        DiningTable table = order == null ? null : order.getBanAn();
        Food food = item.getMonAn();
        Employee requester = item.getNguoiYeuCauHuy();
        Employee handler = item.getNguoiXuLyHuy();
        return new OrderItemCancellationResponse(
                item.getMaChiTiet(),
                order == null ? null : order.getMaDonHang(),
                table == null ? null : table.getMaBan(),
                table == null ? null : table.getTenBan(),
                food == null ? null : food.getMaMonAn(),
                food == null ? null : food.getTenMonAn(),
                item.getSoLuong(),
                item.getTrangThaiMon(),
                item.getTrangThaiHuy(),
                item.getTrangThaiTruocHuy(),
                item.getMaLyDoHuy(),
                item.getLyDoHuy(),
                item.getGhiChuHuy(),
                item.getNguonYeuCauHuy(),
                requester == null ? null : requester.getMaNhanVien(),
                requester == null ? ("KHACH_HANG".equals(item.getNguonYeuCauHuy()) ? "Khách hàng" : null)
                        : requester.getHoTen(),
                item.getThoiGianYeuCauHuy(),
                handler == null ? null : handler.getMaNhanVien(),
                handler == null ? null : handler.getHoTen(),
                item.getThoiGianXuLyHuy(),
                item.getGhiChuXuLyHuy()
        );
    }

    private record CancellationReason(String code, String label, String note) {
    }

    private void validateTableCanOrder(DiningTable table) {
        String status = normalizeStatus(table.getTrangThai());
        if ("BAO_TRI".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn đang bảo trì, không thể đặt món");
        }
        if ("DANG_DON_DEP".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn đang được dọn dẹp, chưa thể đặt món");
        }
        if ("DANG_THANH_TOAN".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn đang thanh toán, không thể gọi thêm món");
        }
        if ("DAT_TRUOC".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn đang ở trạng thái đặt trước, vui lòng liên hệ nhân viên");
        }
    }

    private void syncTableStatusAfterOrderStatus(Order order, String status) {
        DiningTable table = order.getBanAn();
        if (table == null || table.getMaBan() == null) {
            return;
        }
        if ("DA_HUY".equals(status)) {
            boolean hasOtherOpenOrder = orderRepository.existsByBanAn_MaBanAndTrangThaiInAndMaDonHangNot(
                    table.getMaBan(),
                    OPEN_ORDER_STATUSES,
                    order.getMaDonHang()
            );
            if (hasOtherOpenOrder) {
                tableArrangementService.updateServiceStatus(table, "DANG_SU_DUNG");
            } else {
                tableArrangementService.releaseAfterTerminalOrder(table);
            }
        } else if (PAYMENT_PENDING_STATUSES.contains(status)) {
            tableArrangementService.updateServiceStatus(table, "DANG_THANH_TOAN");
        } else {
            tableArrangementService.updateServiceStatus(table, "DANG_SU_DUNG");
        }
    }

    private void synchronizeOrderStatusFromItems(Order order) {
        synchronizeOrderStatusFromItems(order, false);
    }

    private void synchronizeOrderStatusFromItems(Order order, boolean allowReopenFromServed) {
        List<OrderItem> items = order.getChiTietDonHang();
        if (items == null || items.isEmpty()) {
            return;
        }
        String current = normalizeStatus(order.getTrangThai());
        if (Set.of("CHO_XAC_NHAN", "CHO_THANH_TOAN", "SAN_SANG_THANH_TOAN", "DANG_GIAO", "GIAO_THAT_BAI", "DA_THANH_TOAN", "DA_HUY")
                .contains(current)
                || ("DA_PHUC_VU".equals(current) && !allowReopenFromServed)) {
            return;
        }

        boolean hasPendingCancellation = items.stream()
                .anyMatch(item -> ITEM_CANCELLATION_HOLD_STATUS.equals(
                        normalizeStatus(item.getTrangThaiMon())
                ));
        List<OrderItem> activeItems = items.stream()
                .filter(item -> !"DA_HUY".equals(normalizeStatus(item.getTrangThaiMon())))
                .filter(item -> !ITEM_CANCELLATION_HOLD_STATUS.equals(normalizeStatus(item.getTrangThaiMon())))
                .toList();
        if (activeItems.isEmpty()) {
            // Khi toàn bộ món đang chờ duyệt hủy, giữ nguyên trạng thái đơn cho đến khi xử lý xong.
            return;
        }

        boolean allServed = !hasPendingCancellation && activeItems.stream()
                .allMatch(item -> "DA_PHUC_VU".equals(normalizeStatus(item.getTrangThaiMon())));
        boolean anyReadyForService = activeItems.stream()
                .anyMatch(item -> isReadyForServiceStatus(item.getTrangThaiMon()));
        boolean anyCooking = activeItems.stream()
                .anyMatch(item -> isCookingItemStatus(item.getTrangThaiMon()));
        boolean anyWaitingForKitchen = activeItems.stream()
                .anyMatch(item -> "CHO_BEP".equals(normalizeStatus(item.getTrangThaiMon())));

        String nextStatus = current;
        if (allServed) {
            nextStatus = "DA_PHUC_VU";
        } else if (anyReadyForService) {
            // Chỉ cần một món hoàn thành là phục vụ phải nhìn thấy đơn ở nhóm ưu tiên.
            nextStatus = "SAN_SANG_PHUC_VU";
        } else if (anyCooking) {
            nextStatus = "DANG_CHE_BIEN";
        } else if (anyWaitingForKitchen) {
            nextStatus = "DA_XAC_NHAN";
        }

        if (!Objects.equals(current, nextStatus)) {
            syncOrderTimestamps(order, current, nextStatus);
            order.setTrangThai(nextStatus);
        }
    }

    private void validateItemTransition(String oldStatus, String newStatus) {
        if (oldStatus.equals(newStatus)) {
            return;
        }
        if ("DA_HUY".equals(oldStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đã hủy, không thể cập nhật trạng thái");
        }
        if ("DA_PHUC_VU".equals(oldStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đã phục vụ ra bàn, bếp không thể hoàn tác trạng thái");
        }

        // Cho phép bếp hoàn tác khi thao tác nhầm trong phạm vi an toàn:
        // DANG_CHE_BIEN/DANG_NAU -> CHO_BEP và HOAN_THANH/DA_HOAN_THANH -> DANG_CHE_BIEN/DANG_NAU.
        if (isKitchenUndoTransition(oldStatus, newStatus)) {
            return;
        }

        if (isCompletedItemStatus(oldStatus) && !"DA_PHUC_VU".equals(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đã hoàn thành, chỉ có thể hoàn tác về trạng thái đang chế biến");
        }
        if (isCookingItemStatus(oldStatus)
                && !(isCompletedItemStatus(newStatus) || "DA_HUY".equals(newStatus))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Món đang chế biến chỉ có thể hoàn tất hoặc hoàn tác về chờ chế biến");
        }
    }

    private boolean isKitchenUndoTransition(String oldStatus, String newStatus) {
        String oldValue = normalizeStatus(oldStatus);
        String newValue = normalizeStatus(newStatus);
        if (isCookingItemStatus(oldValue) && "CHO_BEP".equals(newValue)) {
            return true;
        }
        return isReadyForServiceStatus(oldValue) && isCookingItemStatus(newValue);
    }

    private boolean isCookingItemStatus(String status) {
        String value = normalizeStatus(status);
        return "DANG_NAU".equals(value) || "DANG_CHE_BIEN".equals(value);
    }

    private boolean isReadyForServiceStatus(String status) {
        String value = normalizeStatus(status);
        return "HOAN_THANH".equals(value)
                || "DA_HOAN_THANH".equals(value)
                || "SAN_SANG".equals(value)
                || "SAN_SANG_PHUC_VU".equals(value);
    }

    private boolean isCompletedItemStatus(String status) {
        String value = normalizeStatus(status);
        return isReadyForServiceStatus(value) || "DA_PHUC_VU".equals(value);
    }

    private void validateAdminOrderTransition(Order order, String oldStatus, String newStatus) {
        if (!"DA_HUY".equals(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin chỉ được theo dõi hoặc hủy đơn hợp lệ; không được thay đổi trạng thái nghiệp vụ"
            );
        }
        if (!ADMIN_CANCELLABLE_ORDER_STATUSES.contains(oldStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể hủy đơn ở trạng thái " + oldStatus
            );
        }

        boolean hasServedItem = order.getChiTietDonHang() != null
                && order.getChiTietDonHang().stream()
                .anyMatch(item -> "DA_PHUC_VU".equals(normalizeStatus(item.getTrangThaiMon())));
        if (hasServedItem) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn đã có món được phục vụ, không thể hủy toàn bộ từ trang quản trị"
            );
        }
    }

    private void validateWaiterOrderTransition(String oldStatus, String newStatus) {
        if (!("CHO_XAC_NHAN".equals(oldStatus) && "DA_XAC_NHAN".equals(newStatus))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nhân viên phục vụ chỉ được xác nhận đơn từ CHO_XAC_NHAN sang DA_XAC_NHAN"
            );
        }
    }

    private Employee resolveActiveEmployeeByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản nhân viên");
        }
        Employee employee = employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên theo tài khoản đăng nhập"
                ));
        ensureEmployeeIsActive(employee);
        return employee;
    }

    private Employee resolveActiveWaiterByUsername(String username) {
        Employee employee = resolveActiveEmployeeByUsername(username);
        String roleName = employee.getVaiTro() == null || employee.getVaiTro().getTenVaiTro() == null
                ? ""
                : employee.getVaiTro().getTenVaiTro().trim().toUpperCase(Locale.ROOT).replace("ROLE_", "");
        if (!"WAITER".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không phải nhân viên phục vụ");
        }
        return employee;
    }

    private void ensureWaiterCanAccessOrder(Employee waiter, Order order) {
        if (order == null || order.getBanAn() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không xác định được bàn của đơn hàng");
        }
        ensureWaiterCanAccessTable(waiter, order.getBanAn());
    }

    private void ensureWaiterCanAccessTable(Employee waiter, DiningTable table) {
        if (waiter == null || !StringUtils.hasText(waiter.getKhuVucPhuTrach())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Nhân viên phục vụ chưa được phân công khu vực"
            );
        }
        String assignedArea = waiter.getKhuVucPhuTrach().trim();
        String tableArea = table != null && StringUtils.hasText(table.getKhuVuc())
                ? table.getKhuVuc().trim()
                : "Khu vực chung";
        if (!assignedArea.equalsIgnoreCase(tableArea)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bàn hoặc đơn hàng không thuộc khu vực được phân công cho nhân viên phục vụ"
            );
        }
    }

    private Employee resolveActiveEmployeeById(Integer employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên: " + employeeId
                ));
        ensureEmployeeIsActive(employee);
        return employee;
    }

    private void ensureEmployeeIsActive(Employee employee) {
        if (!"DANG_LAM_VIEC".equals(normalizeStatus(employee.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhân viên hiện không còn làm việc");
        }
    }

    private void syncOrderTimestamps(Order order, String oldStatus, String newStatus) {
        String oldValue = normalizeStatus(oldStatus);
        String newValue = normalizeStatus(newStatus);
        LocalDateTime now = LocalDateTime.now();

        boolean oldReady = Set.of("SAN_SANG", "SAN_SANG_PHUC_VU", "DA_HOAN_THANH").contains(oldValue);
        boolean newReady = Set.of("SAN_SANG", "SAN_SANG_PHUC_VU", "DA_HOAN_THANH").contains(newValue);
        if (!oldReady && newReady) {
            order.setThoiGianSanSang(now);
        } else if (oldReady && !newReady) {
            order.setThoiGianSanSang(null);
        }

        boolean oldPaymentPending = PAYMENT_PENDING_STATUSES.contains(oldValue);
        boolean newPaymentPending = PAYMENT_PENDING_STATUSES.contains(newValue);
        if (!oldPaymentPending && newPaymentPending) {
            order.setThoiGianYeuCauThanhToan(now);
        } else if (oldPaymentPending && !newPaymentPending) {
            order.setThoiGianYeuCauThanhToan(null);
        }
    }

    private int nextCallNumber(Order order) {
        return order.getChiTietDonHang().stream()
                .map(OrderItem::getLanGoi)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }


    private String mergeNotes(String current, String added) {
        String newNote = trimToNull(added);
        if (newNote == null) {
            return current;
        }
        if (!StringUtils.hasText(current)) {
            return limitLength(newNote, 255);
        }
        if (current.trim().equalsIgnoreCase(newNote)) {
            return current;
        }
        return limitLength(current.trim() + "; " + newNote, 255);
    }

    private String limitLength(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value.replace("#DH", "").replace("#dh", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private DiningTable resolveStaffTableForOrder(Integer tableId) {
        if (tableId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã bàn không được để trống");
        }
        DiningTable selectedTable = diningTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bàn ăn: " + tableId
                ));
        return tableArrangementService.resolvePrimaryTableForUpdate(selectedTable);
    }

    private DiningTable resolveCustomerTableForOrder(OrderCreateRequest request) {
        if (StringUtils.hasText(request.qrToken())) {
            String qrToken = request.qrToken().trim();
            DiningTable selectedTable = diningTableRepository.findByQrTokenForUpdate(qrToken)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã QR không hợp lệ"));
            validateCustomerQrCanOrder(selectedTable);
            return tableArrangementService.resolvePrimaryTableForUpdate(selectedTable);
        }

        // Giữ tương thích tạm thời với frontend cũ trong thời gian chuyển sang QR token.
        if (request.maBan() != null) {
            DiningTable selectedTable = diningTableRepository.findByIdForUpdate(request.maBan())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy bàn ăn: " + request.maBan()
                    ));
            return tableArrangementService.resolvePrimaryTableForUpdate(selectedTable);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token không được để trống");
    }

    private void validateCustomerQrCanOrder(DiningTable table) {
        if (!StringUtils.hasText(table.getQrToken())
                || !StringUtils.hasText(table.getAnhQr())
                || !"DANG_HOAT_DONG".equals(normalizeStatus(table.getTrangThaiQr()))) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Mã QR của bàn đang tạm ngưng hoặc không còn sử dụng"
            );
        }
    }

    private void ensureTableExists(Integer tableId) {
        if (!diningTableRepository.existsById(tableId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn ăn: " + tableId);
        }
    }
}
