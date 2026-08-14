package com.example.restaurant.service;

import com.example.restaurant.config.DeliveryProperties;
import com.example.restaurant.config.OpenRouteServiceProperties;
import com.example.restaurant.config.RestaurantInfoProperties;
import com.example.restaurant.dto.DeliveryHandoverRequest;
import com.example.restaurant.dto.DeliveryOrderCreateRequest;
import com.example.restaurant.dto.DeliveryOrderCreateResponse;
import com.example.restaurant.dto.DeliveryPaymentConfirmRequest;
import com.example.restaurant.dto.DeliveryProviderAssignment;
import com.example.restaurant.dto.DeliveryProviderSimulationRequest;
import com.example.restaurant.dto.DeliveryProviderStatusRequest;
import com.example.restaurant.dto.DeliveryQuoteRequest;
import com.example.restaurant.dto.DeliveryQuoteResponse;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.dto.DeliveryRefundConfirmRequest;
import com.example.restaurant.dto.DeliveryTrackingItemResponse;
import com.example.restaurant.dto.DeliveryTrackingResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.entity.DeliveryRefund;
import com.example.restaurant.entity.Food;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderDelivery;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.repository.DeliveryRefundRepository;
import com.example.restaurant.repository.FoodRepository;
import com.example.restaurant.repository.InvoiceRepository;
import com.example.restaurant.repository.OrderDeliveryRepository;
import com.example.restaurant.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DeliveryOrderService {
    public static final String ORDER_TYPE_DELIVERY = "GIAO_HANG";
    public static final String ORDER_SOURCE_WEBSITE = "WEBSITE";

    private static final String AREA_INNER = "NOI_THANH";
    private static final String AREA_NEARBY = "LAN_CAN";
    private static final String AREA_RADIUS_3 = "BAN_KINH_3KM";
    private static final String AREA_RADIUS_6 = "BAN_KINH_6KM";
    private static final String AREA_RADIUS_10 = "BAN_KINH_10KM";
    private static final String PAYMENT_COD = "COD";
    private static final String PAYMENT_VIETQR = "VIETQR";
    private static final Set<String> PAYMENT_METHODS = Set.of(PAYMENT_COD, PAYMENT_VIETQR);

    private static final String DELIVERY_WAITING_PAYMENT = "CHO_THANH_TOAN";
    private static final String DELIVERY_PENDING_CONFIRMATION = "CHO_XAC_NHAN";
    private static final String DELIVERY_SCHEDULED = "CHO_DEN_GIO";
    private static final String DELIVERY_PREPARING = "DANG_CHUAN_BI";
    private static final String DELIVERY_WAITING_DRIVER = "CHO_TAI_XE_NHAN";
    private static final String LEGACY_DELIVERY_WAITING_HANDOVER = "CHO_BAN_GIAO";
    private static final String DELIVERY_IN_TRANSIT = "DANG_GIAO";
    private static final String DELIVERY_AWAITING_RECONCILIATION = "CHO_DOI_SOAT";
    private static final String DELIVERY_COMPLETED = "HOAN_THANH";
    private static final String DELIVERY_FAILED = "GIAO_THAT_BAI";
    private static final String DELIVERY_CANCELLED = "DA_HUY";

    private static final String PAYMENT_WAITING = "CHO_THANH_TOAN";
    private static final String PAYMENT_PAID = "DA_THANH_TOAN";
    private static final String PAYMENT_REFUND_PENDING = "CHO_HOAN_TIEN";
    private static final String PAYMENT_REFUNDED = "DA_HOAN_TIEN";
    private static final String PAYMENT_EXPIRED = "HET_HAN";
    private static final String PAYMENT_CANCELLED = "DA_HUY";

    private static final String PROVIDER_DELIVERED = "GIAO_THANH_CONG";
    private static final String PROVIDER_FAILED = "GIAO_THAT_BAI";

    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final DeliveryRefundRepository refundRepository;
    private final FoodRepository foodRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrderPricingService orderPricingService;
    private final PromotionService promotionService;
    private final PaymentService paymentService;
    private final DeliveryProperties deliveryProperties;
    private final OpenRouteServiceProperties openRouteServiceProperties;
    private final RestaurantInfoProperties restaurantInfoProperties;
    private final OpenRouteService openRouteService;
    private final DeliveryProviderService deliveryProviderService;
    private final FoodTraceabilityService foodTraceabilityService;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;

    public DeliveryOrderService(OrderRepository orderRepository,
            OrderDeliveryRepository deliveryRepository,
            DeliveryRefundRepository refundRepository,
            FoodRepository foodRepository,
            InvoiceRepository invoiceRepository,
            OrderPricingService orderPricingService,
            PromotionService promotionService,
            PaymentService paymentService,
            DeliveryProperties deliveryProperties,
            OpenRouteServiceProperties openRouteServiceProperties,
            RestaurantInfoProperties restaurantInfoProperties,
            OpenRouteService openRouteService,
            DeliveryProviderService deliveryProviderService,
            FoodTraceabilityService foodTraceabilityService,
            RealtimeNotificationService realtimeNotificationService,
            SystemActivityService systemActivityService) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.refundRepository = refundRepository;
        this.foodRepository = foodRepository;
        this.invoiceRepository = invoiceRepository;
        this.orderPricingService = orderPricingService;
        this.promotionService = promotionService;
        this.paymentService = paymentService;
        this.deliveryProperties = deliveryProperties;
        this.openRouteServiceProperties = openRouteServiceProperties;
        this.restaurantInfoProperties = restaurantInfoProperties;
        this.openRouteService = openRouteService;
        this.deliveryProviderService = deliveryProviderService;
        this.foodTraceabilityService = foodTraceabilityService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional(readOnly = true)
    public DeliveryQuoteResponse quote(DeliveryQuoteRequest request) {
        ensureRestaurantAcceptingOrders();
        return resolveDeliveryQuote(
                request.tinhThanh(),
                request.phuongXa(),
                streetAddress(request.soNha(), request.tenDuong(), request.diaChiChiTiet()),
                request.googlePlaceId(),
                request.googleFormattedAddress());
    }

    @Transactional
    public DeliveryOrderCreateResponse createOrder(DeliveryOrderCreateRequest request) {
        ensureRestaurantAcceptingOrders();
        String clientRequestId = requiredText(request.clientRequestId(), "Mã chống tạo trùng không hợp lệ");
        String recipientPhone = normalizePhone(request.soDienThoaiNhan());
        OrderDelivery duplicated = deliveryRepository.findByClientRequestId(clientRequestId).orElse(null);
        if (duplicated != null) {
            if (!Objects.equals(recipientPhone, duplicated.getSoDienThoaiNhan())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Mã chống tạo trùng đã được sử dụng cho một đơn khác");
            }
            return toCreateResponse(duplicated.getDonHang());
        }

        int totalUnits = request.items().stream()
                .mapToInt(item -> item.soLuong() == null ? 0 : item.soLuong())
                .sum();
        int maxPerItem = positiveOrDefault(deliveryProperties.getMaxUnitsPerItem(), 50);
        int maxPerOrder = positiveOrDefault(deliveryProperties.getMaxUnitsPerOrder(), 100);
        if (totalUnits <= 0 || totalUnits > maxPerOrder
                || request.items().stream().anyMatch(item -> item.soLuong() != null && item.soLuong() > maxPerItem)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mỗi món tối đa " + maxPerItem + " suất và một đơn tối đa " + maxPerOrder + " suất");
        }

        String paymentMethod = normalize(request.phuongThucThanhToan());
        if (!PAYMENT_METHODS.contains(paymentMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ hỗ trợ hai phương thức thanh toán: COD và VIETQR");
        }

        String receiveType = normalize(request.loaiThoiGianNhan());
        LocalDateTime requestedReceiveAt = validateRequestedReceiveTime(
                receiveType,
                request.thoiGianNhanMongMuon());
        String streetAddress = streetAddress(request.soNha(), request.tenDuong(), request.diaChiChiTiet());

        DeliveryQuoteResponse quote = resolveDeliveryQuote(
                request.tinhThanh(),
                request.phuongXa(),
                streetAddress,
                request.googlePlaceId(),
                request.googleFormattedAddress());

        Order order = new Order();
        order.setBanAn(null);
        order.setLoaiDon(ORDER_TYPE_DELIVERY);
        order.setNguonDon(ORDER_SOURCE_WEBSITE);
        order.setTrangThai(PAYMENT_VIETQR.equals(paymentMethod)
                ? DELIVERY_WAITING_PAYMENT
                : DELIVERY_PENDING_CONFIRMATION);
        order.setGhiChu(trimToNull(request.ghiChuDonHang()));
        order.setTamTinh(BigDecimal.ZERO);
        order.setTienGiam(BigDecimal.ZERO);
        order.setTongTien(BigDecimal.ZERO);

        LocalDateTime addedAt = LocalDateTime.now();
        for (var requestedItem : request.items()) {
            Food food = foodRepository.findById(requestedItem.maMonAn())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy món ăn: " + requestedItem.maMonAn()));
            if (!Boolean.TRUE.equals(food.getTrangThai())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Món ăn đang ngừng bán: " + food.getTenMonAn());
            }

            for (int unit = 0; unit < requestedItem.soLuong(); unit++) {
                OrderItem item = new OrderItem();
                item.setMonAn(food);
                item.setSoLuong(1);
                item.setDonGia(food.getGia());
                item.setGhiChu(trimToNull(requestedItem.ghiChu()));
                item.setTrangThaiMon("CHO_BEP");
                item.setLanGoi(1);
                item.setThoiGianThem(addedAt);
                order.addItem(item);
            }
        }

        OrderDelivery delivery = new OrderDelivery();
        delivery.setTrackingToken(UUID.randomUUID().toString());
        delivery.setClientRequestId(clientRequestId);
        delivery.setTenNguoiNhan(requiredText(request.tenNguoiNhan(), "Tên người nhận không hợp lệ"));
        delivery.setSoDienThoaiNhan(recipientPhone);
        delivery.setEmailNguoiNhan(trimToNull(request.emailNguoiNhan()));
        delivery.setSoNha(trimToNull(request.soNha()));
        delivery.setTenDuong(trimToNull(request.tenDuong()));
        delivery.setDiaChiChiTiet(streetAddress);
        delivery.setThongTinDiaChi(trimToNull(request.thongTinDiaChi()));
        delivery.setPhuongXa(trimToNull(request.phuongXa()));
        delivery.setQuanHuyen(trimToNull(request.quanHuyen()));
        delivery.setTinhThanh(trimToNull(request.tinhThanh()));
        delivery.setDiaChiGiaoHang(quote.diaChiDayDu());
        delivery.setKhuVucGiaoHang(quote.khuVucGiaoHang());
        delivery.setGoogleMaps(Boolean.valueOf(quote.googleMaps()));
        delivery.setGooglePlaceId(trimToNull(quote.googlePlaceId()));
        delivery.setQuangDuongMet(quote.quangDuongMet());
        delivery.setThoiGianDuKienGiay(quote.thoiGianDuKienGiay());
        delivery.setGoogleRoutePolyline(trimToNull(quote.encodedPolyline()));
        delivery.setGhiChuGiaoHang(trimToNull(request.ghiChuGiaoHang()));
        delivery.setLoaiThoiGianNhan(receiveType);
        delivery.setThoiGianNhanMongMuon(requestedReceiveAt);
        delivery.setPhiGiaoHang(quote.phiGiaoHang());
        delivery.setPhuongThucThanhToan(paymentMethod);
        delivery.setTrangThaiThanhToan(PAYMENT_WAITING);
        delivery.setSoTienDaThanhToan(BigDecimal.ZERO);
        delivery.setSoTienCanHoan(BigDecimal.ZERO);
        delivery.setSoTienDaHoan(BigDecimal.ZERO);
        delivery.setDaCanhBaoChoXacNhan(false);
        order.setGiaoHang(delivery);

        // Kiểm tra lần cuối ở backend trước khi tạo đơn. Bếp chưa nhận đơn ở bước này.
        foodTraceabilityService.validateAvailabilityForOrder(order);
        orderPricingService.recalculate(order);
        if (StringUtils.hasText(request.maCodeKhuyenMai())) {
            promotionService.applyToNewOrder(order, request.maCodeKhuyenMai());
        }

        LocalDateTime createdAt = LocalDateTime.now();
        if (PAYMENT_VIETQR.equals(paymentMethod)) {
            int timeoutMinutes = positiveOrDefault(deliveryProperties.getPaymentTimeoutMinutes(), 15);
            delivery.setTrangThaiGiaoHang(DELIVERY_WAITING_PAYMENT);
            delivery.setThoiGianHetHanThanhToan(createdAt.plusMinutes(timeoutMinutes));
        } else {
            delivery.setTrangThaiGiaoHang(DELIVERY_PENDING_CONFIRMATION);
            delivery.setThoiGianHetHanThanhToan(null);
        }

        Order savedOrder = orderRepository.saveAndFlush(order);

        if (PAYMENT_VIETQR.equals(paymentMethod)) {
            systemActivityService.record(
                    "DELIVERY_ORDER_CREATED_WAITING_PAYMENT",
                    "Đơn giao hàng #DH" + savedOrder.getMaDonHang()
                            + " đã tạo và đang chờ thanh toán VietQR",
                    savedOrder.getMaDonHang());
            realtimeNotificationService.notifyDeliveryOrderChanged(
                    "DELIVERY_ORDER_WAITING_PAYMENT",
                    "Đơn mới đang chờ khách thanh toán VietQR",
                    savedOrder);
        } else {
            systemActivityService.record(
                    "DELIVERY_ORDER_PENDING_CONFIRMATION",
                    "Đơn giao hàng #DH" + savedOrder.getMaDonHang()
                            + " đang chờ nhà hàng xác nhận",
                    savedOrder.getMaDonHang());
            realtimeNotificationService.notifyDeliveryOrderChanged(
                    "DELIVERY_ORDER_PENDING_CONFIRMATION",
                    "Có đơn giao hàng mới chờ xác nhận",
                    savedOrder);
        }
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return toCreateResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findByLoaiDonOrderByThoiGianDatDescMaDonHangDesc(ORDER_TYPE_DELIVERY);
    }

    @Transactional(readOnly = true)
    public List<Order> findByDeliveryStatus(String status) {
        String normalized = normalize(status);
        return orderRepository.findByLoaiDonOrderByThoiGianDatDescMaDonHangDesc(ORDER_TYPE_DELIVERY)
                .stream()
                .filter(order -> order.getGiaoHang() != null)
                .filter(order -> normalized.equals(normalize(order.getGiaoHang().getTrangThaiGiaoHang())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Order findById(Integer orderId) {
        return requireDeliveryOrder(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng: " + orderId)));
    }

    @Transactional(readOnly = true)
    public DeliveryTrackingResponse track(String trackingToken) {
        OrderDelivery delivery = deliveryRepository.findByTrackingToken(requiredText(
                trackingToken,
                "Mã tra cứu không hợp lệ"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng theo mã tra cứu"));
        return toTrackingResponse(delivery.getDonHang());
    }

    @Transactional(readOnly = true)
    public VietQrResponse createVietQr(String trackingToken) {
        OrderDelivery delivery = deliveryRepository.findByTrackingToken(requiredText(
                trackingToken,
                "Mã tra cứu không hợp lệ"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng theo mã tra cứu"));
        if (!PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không sử dụng phương thức VietQR");
        }
        if (!DELIVERY_WAITING_PAYMENT.equals(normalize(delivery.getTrangThaiGiaoHang()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng hiện không ở bước chờ thanh toán VietQR");
        }
        if (!PAYMENT_WAITING.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không còn chờ thanh toán VietQR");
        }
        if (isPaymentDeadlineExpired(delivery)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thời hạn thanh toán VietQR đã hết");
        }
        return paymentService.createVietQrForDelivery(delivery.getDonHang().getMaDonHang());
    }

    @Transactional
    public Order cancelByCustomer(String trackingToken, DeliveryReasonRequest request) {
        OrderDelivery delivery = deliveryRepository.findByTrackingToken(requiredText(
                trackingToken,
                "Mã tra cứu không hợp lệ"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng theo mã tra cứu"));
        Order order = lockOrder(delivery.getDonHang().getMaDonHang());
        OrderDelivery lockedDelivery = order.getGiaoHang();
        String current = normalize(lockedDelivery.getTrangThaiGiaoHang());
        if (!Set.of(DELIVERY_WAITING_PAYMENT, DELIVERY_PENDING_CONFIRMATION).contains(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn đã được nhà hàng xác nhận. Vui lòng liên hệ nhà hàng nếu cần hỗ trợ hủy");
        }
        return cancelPendingOrder(
                order,
                request.lyDo(),
                "Khách hàng đã hủy đơn trước khi nhà hàng xác nhận",
                PAYMENT_CANCELLED,
                DELIVERY_WAITING_PAYMENT,
                DELIVERY_PENDING_CONFIRMATION);
    }

    @Transactional
    public Order confirmVietQrPayment(Integer orderId, DeliveryPaymentConfirmRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        if (!PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không sử dụng VietQR");
        }
        requireDeliveryStatus(delivery, DELIVERY_WAITING_PAYMENT);
        if (PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            return order;
        }
        if (isPaymentDeadlineExpired(delivery)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thời hạn thanh toán VietQR đã hết");
        }

        String transactionCode = requiredText(request.maGiaoDich(), "Mã giao dịch không hợp lệ");
        if (deliveryRepository.existsByMaGiaoDichIgnoreCase(transactionCode)
                || invoiceRepository.existsByMaGiaoDichIgnoreCase(transactionCode)
                || refundRepository.existsByMaGiaoDichIgnoreCase(transactionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
        }

        orderPricingService.recalculate(order);
        BigDecimal received = money(order.getTongTien());
        delivery.setMaGiaoDich(transactionCode);
        delivery.setSoTienDaThanhToan(received);
        delivery.setGhiChuThanhToan(trimToNull(request.ghiChu()));
        delivery.setThoiGianHetHanThanhToan(null);

        ResponseStatusException inventoryError = null;
        try {
            foodTraceabilityService.validateAvailabilityForOrder(order);
        } catch (ResponseStatusException ex) {
            inventoryError = ex;
        }

        if (inventoryError == null) {
            // Thanh toán xong vẫn phải chờ nhà hàng xác nhận trước khi bếp nhận món.
            delivery.setTrangThaiThanhToan(PAYMENT_PAID);
            delivery.setTrangThaiGiaoHang(DELIVERY_PENDING_CONFIRMATION);
            order.setTrangThai(DELIVERY_PENDING_CONFIRMATION);
            Order savedOrder = orderRepository.saveAndFlush(order);

            systemActivityService.record(
                    "DELIVERY_VIETQR_PAID_PENDING_CONFIRMATION",
                    "VietQR đã được ghi nhận cho đơn giao hàng #DH" + savedOrder.getMaDonHang()
                            + "; đơn đang chờ nhà hàng xác nhận",
                    savedOrder.getMaDonHang());
            realtimeNotificationService.notifyDeliveryOrderChanged(
                    "DELIVERY_PAYMENT_CONFIRMED",
                    "Đã nhận thanh toán VietQR; đơn đang chờ nhà hàng xác nhận",
                    savedOrder);
            realtimeNotificationService.notifyDashboardRefresh(savedOrder);
            return savedOrder;
        }

        // Tiền đã vào nhưng món/ nguyên liệu không còn đủ: hủy và đưa toàn bộ số tiền vào hàng chờ hoàn.
        order.getChiTietDonHang().forEach(item -> item.setTrangThaiMon("DA_HUY"));
        order.setTrangThai(DELIVERY_CANCELLED);
        delivery.setTrangThaiGiaoHang(DELIVERY_CANCELLED);
        delivery.setTrangThaiThanhToan(PAYMENT_REFUND_PENDING);
        delivery.setSoTienCanHoan(received);
        delivery.setLyDoTuChoi(limit(
                "Không thể tiếp tục đơn sau khi nhận thanh toán: " + inventoryError.getReason(),
                500));
        delivery.setThoiGianHuy(LocalDateTime.now());
        promotionService.releaseForCancelledOrder(order);
        orderPricingService.recalculate(order);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_PAYMENT_REQUIRES_REFUND",
                "Đơn #DH" + savedOrder.getMaDonHang()
                        + " đã nhận VietQR nhưng không còn đủ nguyên liệu; chờ hoàn " + received.toPlainString(),
                savedOrder.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_REFUND_REQUIRED",
                "Đơn không thể tiếp tục và đang chờ nhà hàng hoàn lại tiền VietQR",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /** Nhà hàng xác nhận đơn sau khi đã kiểm tra thông tin, món và thanh toán. */
    @Transactional
    public Order confirmOrder(Integer orderId) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_PENDING_CONFIRMATION);

        if (PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))
                && !PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Đơn VietQR chưa được ghi nhận thanh toán");
        }

        foodTraceabilityService.validateAvailabilityForOrder(order);
        orderPricingService.recalculate(order);
        LocalDateTime now = LocalDateTime.now();
        delivery.setThoiGianXacNhan(now);
        delivery.setLyDoTuChoi(null);

        boolean scheduled = shouldWaitForScheduledPreparation(delivery, now);
        if (scheduled) {
            delivery.setTrangThaiGiaoHang(DELIVERY_SCHEDULED);
            order.setTrangThai(DELIVERY_SCHEDULED);
        } else {
            delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
            order.setTrangThai("DA_XAC_NHAN");
        }

        Order savedOrder = orderRepository.saveAndFlush(order);
        systemActivityService.record(
                scheduled ? "DELIVERY_ORDER_CONFIRMED_SCHEDULED" : "DELIVERY_ORDER_CONFIRMED",
                scheduled
                        ? "Đơn giao hàng #DH" + savedOrder.getMaDonHang() + " đã xác nhận và chờ đến giờ chuẩn bị"
                        : "Đơn giao hàng #DH" + savedOrder.getMaDonHang() + " đã được nhà hàng xác nhận và chuyển xuống bếp",
                savedOrder.getMaDonHang());

        if (!scheduled) {
            realtimeNotificationService.notifyKitchenOrderConfirmed(savedOrder);
        }
        realtimeNotificationService.notifyDeliveryOrderChanged(
                scheduled ? "DELIVERY_ORDER_SCHEDULED" : "DELIVERY_ORDER_CONFIRMED",
                scheduled ? "Nhà hàng đã xác nhận đơn; hệ thống sẽ chuyển xuống bếp đúng thời điểm"
                        : "Nhà hàng đã xác nhận đơn và chuyển xuống bếp",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /** Nhà hàng có thể từ chối khi đơn chưa chuyển xuống bếp. */
    @Transactional
    public Order rejectOrder(Integer orderId, DeliveryReasonRequest request) {
        Order order = lockDeliveryOrder(orderId);
        return cancelPendingOrder(
                order,
                request.lyDo(),
                "Nhà hàng đã từ chối đơn giao hàng",
                PAYMENT_CANCELLED,
                DELIVERY_PENDING_CONFIRMATION,
                DELIVERY_SCHEDULED);
    }

    @Transactional
    public Order confirmRefund(Integer orderId, DeliveryRefundConfirmRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        synchronizeRefundState(order, delivery);
        if (!PAYMENT_REFUND_PENDING.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không có khoản tiền đang chờ hoàn");
        }

        BigDecimal refundAmount = money(delivery.getSoTienCanHoan());
        if (refundAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền cần hoàn không hợp lệ");
        }
        String transactionCode = requiredText(request.maGiaoDich(), "Mã giao dịch hoàn tiền không hợp lệ");
        if (refundRepository.existsByMaGiaoDichIgnoreCase(transactionCode)
                || deliveryRepository.existsByMaGiaoDichIgnoreCase(transactionCode)
                || invoiceRepository.existsByMaGiaoDichIgnoreCase(transactionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
        }

        DeliveryRefund refund = new DeliveryRefund();
        refund.setGiaoHang(delivery);
        refund.setSoTien(refundAmount);
        refund.setMaGiaoDich(transactionCode);
        refund.setGhiChu(trimToNull(request.ghiChu()));
        refundRepository.saveAndFlush(refund);

        delivery.setSoTienDaHoan(money(delivery.getSoTienDaHoan()).add(refundAmount));
        delivery.setSoTienCanHoan(BigDecimal.ZERO.setScale(2));
        boolean cancelled = DELIVERY_CANCELLED.equals(normalize(delivery.getTrangThaiGiaoHang()))
                || "DA_HUY".equals(normalize(order.getTrangThai()));
        delivery.setTrangThaiThanhToan(cancelled ? PAYMENT_REFUNDED : PAYMENT_PAID);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_REFUND_CONFIRMED",
                "Đã hoàn " + refundAmount.toPlainString() + " cho đơn giao hàng #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_REFUND_CONFIRMED",
                cancelled ? "Nhà hàng đã hoàn tiền cho đơn bị hủy" : "Nhà hàng đã hoàn phần tiền chênh lệch",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /**
     * Được OrderService gọi sau mỗi lần bếp cập nhật một suất món.
     * Tài xế được điều phối theo thời điểm món dự kiến sẵn sàng; chỉ khi toàn bộ
     * món hoàn thành đơn mới chuyển sang trạng thái sẵn sàng bàn giao.
     */
    @Transactional
    public void synchronizeAfterKitchenUpdate(Order order) {
        if (!isDeliveryOrder(order) || order.getGiaoHang() == null) {
            return;
        }
        OrderDelivery delivery = order.getGiaoHang();
        String deliveryStatus = normalize(delivery.getTrangThaiGiaoHang());
        if (Set.of(
                DELIVERY_WAITING_PAYMENT,
                DELIVERY_PENDING_CONFIRMATION,
                DELIVERY_SCHEDULED,
                DELIVERY_IN_TRANSIT,
                DELIVERY_AWAITING_RECONCILIATION,
                DELIVERY_COMPLETED,
                DELIVERY_FAILED,
                DELIVERY_CANCELLED).contains(deliveryStatus)) {
            return;
        }

        orderPricingService.recalculate(order);
        synchronizeRefundState(order, delivery);

        boolean hasPendingCancellation = order.getChiTietDonHang().stream()
                .anyMatch(item -> "YEU_CAU_HUY".equals(normalize(item.getTrangThaiMon())));
        if (hasPendingCancellation) {
            delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
            clearProviderAssignment(delivery);
            clearProviderResult(delivery);
            delivery.setThoiGianSanSang(null);
            order.setThoiGianSanSang(null);
            order.setTrangThai("DANG_CHE_BIEN");
            return;
        }

        List<OrderItem> activeItems = order.getChiTietDonHang().stream()
                .filter(item -> !"DA_HUY".equals(normalize(item.getTrangThaiMon())))
                .toList();
        if (activeItems.isEmpty()) {
            order.setTrangThai(DELIVERY_CANCELLED);
            delivery.setTrangThaiGiaoHang(DELIVERY_CANCELLED);
            delivery.setThoiGianHuy(
                    delivery.getThoiGianHuy() == null ? LocalDateTime.now() : delivery.getThoiGianHuy());
            clearProviderAssignment(delivery);
            clearProviderResult(delivery);
            delivery.setThoiGianSanSang(null);
            order.setThoiGianSanSang(null);
            orderPricingService.recalculate(order);
            synchronizeRefundState(order, delivery);
            if (money(delivery.getSoTienDaThanhToan()).signum() <= 0) {
                delivery.setTrangThaiThanhToan(PAYMENT_CANCELLED);
            }
            return;
        }

        long completedCount = activeItems.stream()
                .filter(item -> isKitchenCompleted(item.getTrangThaiMon()))
                .count();
        boolean allCompleted = completedCount == activeItems.size();
        boolean anyStarted = activeItems.stream()
                .anyMatch(item -> !"CHO_BEP".equals(normalize(item.getTrangThaiMon())));

        // Không dùng ngưỡng phần trăm món hoàn thành nữa. Điều phối theo thời điểm
        // món dự kiến sẵn sàng để giảm thời gian tài xế phải chờ tại nhà hàng.
        if (!allCompleted && anyStarted && shouldPreassignDriver(order, delivery) && !hasProviderAssignment(delivery)) {
            assignDeliveryProvider(order, delivery);
            realtimeNotificationService.notifyDeliveryOrderChanged(
                    "DELIVERY_DRIVER_PREASSIGNED",
                    "Món sắp sẵn sàng; hệ thống đã điều phối đơn vị vận chuyển",
                    order);
            systemActivityService.record(
                    "DELIVERY_DRIVER_PREASSIGNED",
                    "Đã điều phối tài xế theo thời điểm món dự kiến sẵn sàng cho đơn #DH"
                            + order.getMaDonHang(),
                    order.getMaDonHang());
        }

        if (allCompleted) {
            if (!hasProviderAssignment(delivery)) {
                assignDeliveryProvider(order, delivery);
            }
            LocalDateTime readyAt = delivery.getThoiGianSanSang();
            if (readyAt == null) {
                readyAt = LocalDateTime.now();
                delivery.setThoiGianSanSang(readyAt);
            }
            if (order.getThoiGianSanSang() == null) {
                order.setThoiGianSanSang(readyAt);
            }
            delivery.setTrangThaiGiaoHang(DELIVERY_WAITING_DRIVER);
            order.setTrangThai(DELIVERY_WAITING_DRIVER);
            realtimeNotificationService.notifyDeliveryOrderChanged(
                    "DELIVERY_READY_FOR_HANDOVER",
                    "Toàn bộ món đã hoàn thành và sẵn sàng bàn giao cho tài xế đã được điều phối",
                    order);
            return;
        }

        if (Set.of(DELIVERY_WAITING_DRIVER, LEGACY_DELIVERY_WAITING_HANDOVER).contains(deliveryStatus)) {
            // Nếu bếp hoàn tác trạng thái món, vẫn giữ tài xế đã điều phối nhưng đưa đơn về
            // chuẩn bị.
            delivery.setThoiGianSanSang(null);
        }
        order.setThoiGianSanSang(null);
        delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
        order.setTrangThai(anyStarted ? "DANG_CHE_BIEN" : "DA_XAC_NHAN");
    }

    @Transactional
    public Order handover(Integer orderId, DeliveryHandoverRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireAnyDeliveryStatus(delivery, DELIVERY_WAITING_DRIVER, LEGACY_DELIVERY_WAITING_HANDOVER);
        if (!hasProviderAssignment(delivery)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn chưa được đơn vị vận chuyển cấp mã vận đơn và điều phối tài xế");
        }
        if (PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))
                && !isPaymentSettledForCurrentTotal(order, delivery)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Thanh toán VietQR hoặc hoàn tiền chênh lệch chưa được đối soát xong");
        }

        delivery.setGhiChuBanGiao(request == null ? null : trimToNull(request.ghiChuBanGiao()));
        delivery.setTrangThaiGiaoHang(DELIVERY_IN_TRANSIT);
        delivery.setThoiGianBanGiao(LocalDateTime.now());
        delivery.setLyDoGiaoThatBai(null);
        clearProviderResult(delivery);
        order.setTrangThai(DELIVERY_IN_TRANSIT);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_HANDED_OVER",
                "Đơn " + delivery.getMaVanChuyen() + " đã bàn giao cho tài xế "
                        + delivery.getTenNguoiGiao() + " của " + delivery.getDonViVanChuyen(),
                savedOrder.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_IN_TRANSIT",
                "Đơn hàng đã được bàn giao; trạng thái giao sẽ được đồng bộ từ đối tác vận chuyển",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /**
     * Bước nội bộ sau khi đối tác đã báo giao thành công: ghi nhận hóa đơn/kế toán.
     * Trạng thái này không còn xuất hiện trong hành trình theo dõi của khách.
     */
    @Transactional
    public Order complete(Integer orderId, String username) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_AWAITING_RECONCILIATION);
        if (!PROVIDER_DELIVERED.equals(normalize(delivery.getTrangThaiDoiTac()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đối tác vận chuyển chưa báo giao thành công");
        }

        String paymentMethod = normalize(delivery.getPhuongThucThanhToan());
        if (PAYMENT_VIETQR.equals(paymentMethod) && !isPaymentSettledForCurrentTotal(order, delivery)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chưa thể hoàn tất vì thanh toán/hoàn tiền VietQR chưa được đối soát đủ");
        }
        if (PAYMENT_COD.equals(paymentMethod)) {
            delivery.setTrangThaiThanhToan(PAYMENT_PAID);
            delivery.setSoTienDaThanhToan(money(order.getTongTien()));
        }

        delivery.setTrangThaiGiaoHang(DELIVERY_COMPLETED);
        delivery.setThoiGianGiaoThanhCong(
                delivery.getThoiGianCapNhatDoiTac() == null ? LocalDateTime.now()
                        : delivery.getThoiGianCapNhatDoiTac());
        paymentService.completeDeliveryPayment(order, username);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_COMPLETED",
                "Đơn " + delivery.getMaVanChuyen() + " đã được ghi nhận hóa đơn sau giao thành công",
                savedOrder.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_COMPLETED",
                "Đã hoàn tất ghi nhận nội bộ cho đơn giao thành công",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /** Fallback thủ công nếu webhook đối tác gặp sự cố. */
    @Transactional
    public Order fail(Integer orderId, DeliveryReasonRequest request) {
        Order order = lockDeliveryOrder(orderId);
        return applyProviderResultLocked(
                order,
                PROVIDER_FAILED,
                requiredText(request.lyDo(), "Vui lòng nhập lý do giao thất bại"),
                "MANUAL_FALLBACK",
                "manual-" + UUID.randomUUID());
    }

    @Transactional
    public Order simulateProviderResult(Integer orderId, DeliveryProviderSimulationRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        if (!StringUtils.hasText(delivery.getMaVanChuyen())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn chưa có mã vận đơn để mô phỏng webhook");
        }
        return applyProviderResultLocked(
                order,
                normalizeProviderStatus(request.trangThai()),
                trimToNull(request.lyDo()),
                "DEMO_WEBHOOK",
                "demo-" + UUID.randomUUID());
    }

    @Transactional
    public Order applyProviderWebhook(DeliveryProviderStatusRequest request) {
        String waybill = requiredText(request.maVanDon(), "Mã vận đơn không hợp lệ");
        OrderDelivery found = deliveryRepository.findByMaVanChuyen(waybill)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã vận đơn"));
        Order order = lockDeliveryOrder(found.getDonHang().getMaDonHang());
        if (!waybill.equals(order.getGiaoHang().getMaVanChuyen())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã vận đơn không khớp với đơn đang khóa");
        }
        return applyProviderResultLocked(
                order,
                normalizeProviderStatus(request.trangThai()),
                trimToNull(request.lyDo()),
                "PROVIDER_WEBHOOK",
                trimToNull(request.eventId()));
    }

    private Order applyProviderResultLocked(Order order,
            String providerStatus,
            String reason,
            String source,
            String eventId) {
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_IN_TRANSIT);

        if (StringUtils.hasText(eventId) && eventId.equals(delivery.getMaSuKienDoiTac())) {
            return order;
        }
        delivery.setTrangThaiDoiTac(providerStatus);
        delivery.setLyDoDoiTac(reason);
        delivery.setNguonCapNhatDoiTac(source);
        delivery.setMaSuKienDoiTac(eventId);
        delivery.setThoiGianCapNhatDoiTac(LocalDateTime.now());

        if (PROVIDER_DELIVERED.equals(providerStatus)) {
            // Khách được coi là đã nhận hàng ngay khi đối tác báo giao thành công.
            // CHO_DOI_SOAT chỉ còn là trạng thái nội bộ để thu ngân ghi nhận hóa đơn.
            delivery.setTrangThaiGiaoHang(DELIVERY_AWAITING_RECONCILIATION);
            delivery.setThoiGianGiaoThanhCong(delivery.getThoiGianCapNhatDoiTac());
            if (PAYMENT_COD.equals(normalize(delivery.getPhuongThucThanhToan()))) {
                delivery.setTrangThaiThanhToan(PAYMENT_PAID);
                delivery.setSoTienDaThanhToan(money(order.getTongTien()));
            }
            order.setTrangThai(DELIVERY_AWAITING_RECONCILIATION);
        } else {
            delivery.setTrangThaiGiaoHang(DELIVERY_FAILED);
            delivery.setLyDoGiaoThatBai(StringUtils.hasText(reason) ? reason : "Đơn vị vận chuyển báo giao thất bại");
            order.setTrangThai(DELIVERY_FAILED);
        }
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_PROVIDER_STATUS_UPDATED",
                "Đối tác vận chuyển cập nhật đơn " + delivery.getMaVanChuyen() + " thành " + providerStatus,
                savedOrder.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_PROVIDER_STATUS_UPDATED",
                PROVIDER_DELIVERED.equals(providerStatus)
                        ? "Đơn đã giao thành công; phần ghi nhận hóa đơn được xử lý nội bộ"
                        : "Đối tác báo giao thất bại",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order retry(Integer orderId) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_FAILED);

        clearProviderAssignment(delivery);
        clearProviderResult(delivery);
        delivery.setGhiChuBanGiao(null);
        delivery.setThoiGianBanGiao(null);
        delivery.setLyDoGiaoThatBai(null);
        assignDeliveryProvider(order, delivery);
        delivery.setTrangThaiGiaoHang(DELIVERY_WAITING_DRIVER);
        order.setTrangThai(DELIVERY_WAITING_DRIVER);
        Order savedOrder = orderRepository.saveAndFlush(order);

        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_RETRY_DRIVER_ASSIGNED",
                "Đơn vị vận chuyển đã điều phối lại tài xế cho đơn giao lại",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    /** Chạy định kỳ: điều phối tài xế theo ETA và tự hủy VietQR hết hạn. */
    @Transactional
    public void performMaintenance() {
        List<Order> orders = orderRepository.findByLoaiDonOrderByThoiGianDatDescMaDonHangDesc(ORDER_TYPE_DELIVERY);
        for (Order order : orders) {
            if (order.getGiaoHang() == null) {
                continue;
            }
            OrderDelivery delivery = order.getGiaoHang();
            String status = normalize(delivery.getTrangThaiGiaoHang());

            if (DELIVERY_SCHEDULED.equals(status) && shouldReleaseScheduledOrder(delivery, LocalDateTime.now())) {
                delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
                order.setTrangThai("DA_XAC_NHAN");
                orderRepository.saveAndFlush(order);
                systemActivityService.record(
                        "DELIVERY_SCHEDULED_RELEASED",
                        "Đơn giao hàng #DH" + order.getMaDonHang() + " đã đến thời điểm chuẩn bị và được chuyển xuống bếp",
                        order.getMaDonHang());
                realtimeNotificationService.notifyKitchenOrderConfirmed(order);
                realtimeNotificationService.notifyDeliveryOrderChanged(
                        "DELIVERY_SCHEDULED_RELEASED",
                        "Đơn hẹn giờ đã đến thời điểm chuẩn bị và được chuyển xuống bếp",
                        order);
                realtimeNotificationService.notifyDashboardRefresh(order);
                status = DELIVERY_PREPARING;
            }

            if (DELIVERY_PREPARING.equals(status)
                    && !hasProviderAssignment(delivery)
                    && hasKitchenStarted(order)
                    && shouldPreassignDriver(order, delivery)) {
                assignDeliveryProvider(order, delivery);
                orderRepository.saveAndFlush(order);
                systemActivityService.record(
                        "DELIVERY_DRIVER_PREASSIGNED",
                        "Hệ thống tự điều phối tài xế theo ETA cho đơn #DH" + order.getMaDonHang(),
                        order.getMaDonHang());
                realtimeNotificationService.notifyDeliveryOrderChanged(
                        "DELIVERY_DRIVER_PREASSIGNED",
                        "Món sắp sẵn sàng; đơn vị vận chuyển đã được điều phối",
                        order);
                realtimeNotificationService.notifyDashboardRefresh(order);
            }

            if (DELIVERY_WAITING_PAYMENT.equals(status)
                    && PAYMENT_WAITING.equals(normalize(delivery.getTrangThaiThanhToan()))
                    && isPaymentDeadlineExpired(delivery)) {
                expireUnpaidVietQrOrder(order);
            }
        }
    }

    private void expireUnpaidVietQrOrder(Order order) {
        OrderDelivery delivery = order.getGiaoHang();
        order.getChiTietDonHang().forEach(item -> item.setTrangThaiMon("DA_HUY"));
        order.setTrangThai(DELIVERY_CANCELLED);
        delivery.setTrangThaiGiaoHang(DELIVERY_CANCELLED);
        delivery.setTrangThaiThanhToan(PAYMENT_EXPIRED);
        delivery.setLyDoTuChoi("Đơn VietQR tự hủy vì quá thời hạn thanh toán");
        delivery.setThoiGianHuy(LocalDateTime.now());
        delivery.setThoiGianHetHanThanhToan(null);
        promotionService.releaseForCancelledOrder(order);
        orderPricingService.recalculate(order);
        orderRepository.saveAndFlush(order);
        systemActivityService.record(
                "DELIVERY_PAYMENT_EXPIRED",
                "Đơn giao hàng #DH" + order.getMaDonHang() + " đã tự hủy vì VietQR hết hạn",
                order.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_PAYMENT_EXPIRED",
                "Đơn đã tự hủy do quá thời hạn thanh toán VietQR",
                order);
        realtimeNotificationService.notifyDashboardRefresh(order);
    }

    private Order cancelPendingOrder(Order order,
            String reason,
            String activityMessage,
            String unpaidPaymentStatus,
            String... acceptedStatuses) {
        OrderDelivery delivery = order.getGiaoHang();
        requireAnyDeliveryStatus(delivery, acceptedStatuses);

        order.getChiTietDonHang().forEach(item -> item.setTrangThaiMon("DA_HUY"));
        order.setTrangThai(DELIVERY_CANCELLED);
        delivery.setTrangThaiGiaoHang(DELIVERY_CANCELLED);
        delivery.setLyDoTuChoi(requiredText(reason, "Vui lòng nhập lý do hủy hoặc từ chối"));
        delivery.setThoiGianHuy(LocalDateTime.now());
        delivery.setThoiGianHetHanThanhToan(null);
        clearProviderAssignment(delivery);
        clearProviderResult(delivery);
        promotionService.releaseForCancelledOrder(order);
        orderPricingService.recalculate(order);
        synchronizeRefundState(order, delivery);
        if (money(delivery.getSoTienDaThanhToan()).signum() <= 0) {
            delivery.setTrangThaiThanhToan(unpaidPaymentStatus);
        }
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_ORDER_CANCELLED",
                activityMessage + " #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang());
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_ORDER_CANCELLED",
                "Đơn giao hàng đã bị hủy",
                savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    private void synchronizeRefundState(Order order, OrderDelivery delivery) {
        if (delivery == null || !PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))) {
            return;
        }
        BigDecimal paid = money(delivery.getSoTienDaThanhToan());
        BigDecimal refunded = money(delivery.getSoTienDaHoan());
        BigDecimal netPaid = paid.subtract(refunded).max(BigDecimal.ZERO.setScale(2));
        if (paid.signum() <= 0) {
            delivery.setSoTienCanHoan(BigDecimal.ZERO.setScale(2));
            return;
        }

        boolean cancelled = DELIVERY_CANCELLED.equals(normalize(delivery.getTrangThaiGiaoHang()))
                || "DA_HUY".equals(normalize(order.getTrangThai()));
        BigDecimal required = cancelled ? BigDecimal.ZERO.setScale(2) : money(order.getTongTien());
        BigDecimal due = netPaid.subtract(required).max(BigDecimal.ZERO.setScale(2));
        delivery.setSoTienCanHoan(money(due));
        if (due.signum() > 0) {
            delivery.setTrangThaiThanhToan(PAYMENT_REFUND_PENDING);
        } else if (cancelled && paid.signum() > 0 && refunded.compareTo(paid) >= 0) {
            delivery.setTrangThaiThanhToan(PAYMENT_REFUNDED);
        } else if (!cancelled && netPaid.compareTo(required) >= 0) {
            delivery.setTrangThaiThanhToan(PAYMENT_PAID);
        }
    }

    private boolean isPaymentSettledForCurrentTotal(Order order, OrderDelivery delivery) {
        synchronizeRefundState(order, delivery);
        if (PAYMENT_REFUND_PENDING.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            return false;
        }
        BigDecimal netPaid = money(delivery.getSoTienDaThanhToan())
                .subtract(money(delivery.getSoTienDaHoan()));
        return netPaid.compareTo(money(order.getTongTien())) >= 0;
    }

    private boolean isPaymentDeadlineExpired(OrderDelivery delivery) {
        return delivery != null
                && delivery.getThoiGianHetHanThanhToan() != null
                && !delivery.getThoiGianHetHanThanhToan().isAfter(LocalDateTime.now());
    }

    private Order lockDeliveryOrder(Integer orderId) {
        return requireDeliveryOrder(lockOrder(orderId));
    }

    private Order lockOrder(Integer orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId));
    }

    private Order requireDeliveryOrder(Order order) {
        if (!isDeliveryOrder(order) || order.getGiaoHang() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn giao hàng");
        }
        return order;
    }

    private boolean isDeliveryOrder(Order order) {
        return order != null && ORDER_TYPE_DELIVERY.equals(normalize(order.getLoaiDon()));
    }

    private void requireDeliveryStatus(OrderDelivery delivery, String requiredStatus) {
        String current = normalize(delivery.getTrangThaiGiaoHang());
        if (!requiredStatus.equals(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể thực hiện thao tác khi đơn đang ở trạng thái " + current);
        }
    }

    private String streetAddress(String houseNumber, String streetName, String legacyDetailedAddress) {
        String house = trimToNull(houseNumber);
        String street = trimToNull(streetName);
        if (house != null || street != null) {
            String value = String.join(" ", java.util.stream.Stream.of(house, street)
                    .filter(Objects::nonNull)
                    .toList()).trim();
            if (value.length() < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Vui lòng nhập số nhà và tên đường cụ thể");
            }
            return value;
        }
        return requiredText(legacyDetailedAddress,
                "Vui lòng nhập số nhà và tên đường giao hàng");
    }

    private LocalDateTime validateRequestedReceiveTime(String receiveType, LocalDateTime requestedAt) {
        if ("SOM_NHAT".equals(receiveType)) {
            return null;
        }
        if (!"HEN_GIO".equals(receiveType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hình thức thời gian nhận chỉ hỗ trợ SOM_NHAT hoặc HEN_GIO");
        }
        if (requestedAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn thời gian nhận mong muốn");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int minAdvance = positiveOrDefault(deliveryProperties.getScheduledMinAdvanceMinutes(), 30);
        int maxDays = positiveOrDefault(deliveryProperties.getScheduledMaxAdvanceDays(), 7);
        if (requestedAt.isBefore(now.plusMinutes(minAdvance))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn hẹn giờ phải cách hiện tại ít nhất " + minAdvance + " phút");
        }
        if (requestedAt.isAfter(now.plusDays(maxDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ hỗ trợ hẹn giờ trong tối đa " + maxDays + " ngày tới");
        }
        if (!ReservationPolicyValidator.isWithinOpeningHours(
                requestedAt.minusMinutes(1),
                requestedAt.plusMinutes(1),
                restaurantInfoProperties.getOpeningHours())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian hẹn nằm ngoài giờ hoạt động của nhà hàng: "
                            + restaurantInfoProperties.getOpeningHours());
        }
        return requestedAt;
    }

    private boolean shouldWaitForScheduledPreparation(OrderDelivery delivery, LocalDateTime now) {
        return delivery != null
                && delivery.getThoiGianNhanMongMuon() != null
                && scheduledPreparationStart(delivery).isAfter(now);
    }

    private boolean shouldReleaseScheduledOrder(OrderDelivery delivery, LocalDateTime now) {
        return delivery != null
                && delivery.getThoiGianNhanMongMuon() != null
                && !scheduledPreparationStart(delivery).isAfter(now);
    }

    private LocalDateTime scheduledPreparationStart(OrderDelivery delivery) {
        LocalDateTime receiveAt = delivery.getThoiGianNhanMongMuon();
        if (receiveAt == null) {
            return LocalDateTime.now();
        }
        long preparationSeconds = positiveOrDefault(deliveryProperties.getPreparationMinutes(), 25) * 60L;
        return receiveAt.minusSeconds(preparationSeconds + deliveryTravelSeconds(delivery));
    }

    private long deliveryTravelSeconds(OrderDelivery delivery) {
        return delivery != null && delivery.getThoiGianDuKienGiay() != null && delivery.getThoiGianDuKienGiay() > 0
                ? delivery.getThoiGianDuKienGiay()
                : positiveOrDefault(deliveryProperties.getFallbackDeliveryMinutes(), 20) * 60L;
    }

    private DeliveryQuoteResponse resolveDeliveryQuote(String city,
            String ward,
            String detailedAddress,
            String legacyPlaceId,
            String legacyFormattedAddress) {
        String cityText = trimToNull(city);
        String wardText = trimToNull(ward);
        String detailText = trimToNull(detailedAddress);

        /*
         * Ưu tiên tuyệt đối dữ liệu địa chỉ có cấu trúc do form hiện tại gửi lên.
         * Không để googleFormattedAddress/legacyFormattedAddress cũ ghi đè rồi làm
         * mất phường/xã đã chọn trên frontend.
         */
        String structuredAddress = joinAddress(detailText, wardText, cityText);
        String fullAddress = StringUtils.hasText(structuredAddress)
                ? structuredAddress
                : trimToNull(legacyFormattedAddress);

        String supportedCityText = StringUtils.hasText(deliveryProperties.getSupportedCity())
                ? deliveryProperties.getSupportedCity().trim()
                : "Đà Nẵng";
        String supportedCity = normalizeLocationKey(supportedCityText);

        // Nếu frontend đã tách riêng tỉnh/thành phố thì chặn ngay trước khi gọi bản đồ.
        if (StringUtils.hasText(cityText)
                && !normalizeLocationKey(cityText).equals(supportedCity)) {
            throw unsupportedDeliveryCity(supportedCityText);
        }

        // Bản đồ miễn phí: backend tự geocode địa chỉ và tính tuyến đường bằng
        // openrouteservice (dữ liệu OpenStreetMap). Trình duyệt không cần API key.
        if (openRouteService.isConfigured() && StringUtils.hasText(fullAddress)) {
            validateTypedDeliveryAddress(fullAddress);
            OpenRouteService.RouteResult route = openRouteService.computeRouteToAddress(
                    fullAddress,
                    wardText,
                    cityText);

            // Với ô địa chỉ nhập tự do, xác minh lại địa phương mà Pelias thực sự trả về.
            // Nhờ vậy địa chỉ ở Huế/Quảng Nam... không thể bị nhận nhầm thành đường cùng tên
            // tại Đà Nẵng chỉ vì focus.point đặt gần nhà hàng.
            String resolvedLocationContext = normalizeLocationKey(route.resolvedLocationContext());
            if (StringUtils.hasText(resolvedLocationContext)
                    && !resolvedLocationContext.contains(supportedCity)) {
                throw unsupportedDeliveryCity(supportedCityText);
            }
            double distanceKm = route.distanceMeters() / 1000.0d;
            double tier1 = positiveDoubleOrDefault(openRouteServiceProperties.getTier1DistanceKm(), 3.0d);
            double tier2 = positiveDoubleOrDefault(openRouteServiceProperties.getTier2DistanceKm(), 6.0d);
            double max = positiveDoubleOrDefault(openRouteServiceProperties.getMaxDeliveryDistanceKm(), 10.0d);
            if (tier2 < tier1 || max < tier2) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Cấu hình bán kính giao hàng openrouteservice không hợp lệ"
                );
            }
            if (distanceKm > max) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format(
                                Locale.ROOT,
                                "Địa chỉ cách nhà hàng %.1f km, vượt phạm vi giao tối đa %.1f km",
                                distanceKm,
                                max
                        )
                );
            }

            String area;
            String areaLabel;
            BigDecimal fee;
            if (distanceKm <= tier1) {
                area = AREA_RADIUS_3;
                areaLabel = String.format(Locale.ROOT, "Trong %.0f km", tier1);
                fee = configuredFee(openRouteServiceProperties.getTier1Fee());
            } else if (distanceKm <= tier2) {
                area = AREA_RADIUS_6;
                areaLabel = String.format(Locale.ROOT, "Từ %.0f đến %.0f km", tier1, tier2);
                fee = configuredFee(openRouteServiceProperties.getTier2Fee());
            } else {
                area = AREA_RADIUS_10;
                areaLabel = String.format(Locale.ROOT, "Từ %.0f đến %.0f km", tier2, max);
                fee = configuredFee(openRouteServiceProperties.getTier3Fee());
            }

            return new DeliveryQuoteResponse(
                    cityText,
                    null,
                    wardText,
                    area,
                    areaLabel,
                    fee,
                    StringUtils.hasText(route.resolvedAddress()) ? route.resolvedAddress() : fullAddress,
                    false, // legacy field googleMaps: không dùng Google Maps nữa
                    null,
                    route.distanceMeters(),
                    route.durationSeconds(),
                    estimatedCustomerEtaSeconds(route.durationSeconds()),
                    route.routeGeometry()
            );
        }

        // Cấu trúc địa chỉ mới không còn dùng quận/huyện để suy ra vùng giao hàng.
        // Vì phí được tính theo quãng đường thực tế, OpenRouteService phải được cấu hình.
        String normalizedCity = normalizeLocationKey(requiredText(city, "Tỉnh/thành phố không hợp lệ"));
        if (!normalizedCity.equals(supportedCity)) {
            throw unsupportedDeliveryCity(supportedCityText);
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Dịch vụ tính khoảng cách giao hàng chưa được cấu hình. Vui lòng cấu hình openrouteservice ở backend"
        );
    }

    private ResponseStatusException unsupportedDeliveryCity(String supportedCity) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Hiện tại nhà hàng chỉ hỗ trợ giao hàng trong " + supportedCity);
    }

    private void validateTypedDeliveryAddress(String fullAddress) {
        String normalized = normalizeLocationKey(fullAddress);
        int wordCount = normalized.isBlank() ? 0 : normalized.split("\\s+").length;
        boolean hasNumber = fullAddress.chars().anyMatch(Character::isDigit);
        long commaCount = fullAddress.chars().filter(ch -> ch == ',').count();

        // Tránh trường hợp khách chỉ gõ tên tỉnh/thành hoặc khu vực rất rộng như "Đà Nẵng".
        // Vẫn cho phép địa chỉ không có số nhà nếu là tên địa điểm đủ cụ thể.
        if (wordCount < 3 || (!hasNumber && commaCount < 2 && wordCount < 5)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập địa chỉ cụ thể hơn (số nhà, tên đường, phường/xã...) để tính phí giao hàng"
            );
        }
    }

    private void ensureRestaurantAcceptingOrders() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (!ReservationPolicyValidator.isWithinOpeningHours(
                now,
                now.plusMinutes(1),
                restaurantInfoProperties.getOpeningHours()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nhà hàng hiện ngoài giờ nhận đơn. Giờ hoạt động: "
                            + restaurantInfoProperties.getOpeningHours()
            );
        }
    }

    private long estimatedCustomerEtaSeconds(Long routeDurationSeconds) {
        long preparationSeconds = positiveOrDefault(deliveryProperties.getPreparationMinutes(), 25) * 60L;
        long deliverySeconds = routeDurationSeconds != null && routeDurationSeconds > 0
                ? routeDurationSeconds
                : positiveOrDefault(deliveryProperties.getFallbackDeliveryMinutes(), 20) * 60L;
        return preparationSeconds + deliverySeconds;
    }

    private boolean shouldPreassignDriver(Order order, OrderDelivery delivery) {
        int preparationMinutes = positiveOrDefault(deliveryProperties.getPreparationMinutes(), 25);
        int leadMinutes = Math.min(
                preparationMinutes,
                positiveOrDefault(deliveryProperties.getDriverAssignmentLeadMinutes(), 8)
        );

        LocalDateTime dispatchAt;
        if (delivery != null && delivery.getThoiGianNhanMongMuon() != null) {
            dispatchAt = delivery.getThoiGianNhanMongMuon()
                    .minusSeconds(deliveryTravelSeconds(delivery))
                    .minusMinutes(leadMinutes);
        } else {
            LocalDateTime acceptedAt = delivery != null && delivery.getThoiGianXacNhan() != null
                    ? delivery.getThoiGianXacNhan()
                    : order.getThoiGianDat();
            if (acceptedAt == null) {
                return false;
            }
            dispatchAt = acceptedAt.plusMinutes(preparationMinutes - leadMinutes);
        }
        return !LocalDateTime.now().isBefore(dispatchAt);
    }

    private boolean hasKitchenStarted(Order order) {
        return order != null && order.getChiTietDonHang() != null
                && order.getChiTietDonHang().stream()
                .filter(item -> !"DA_HUY".equals(normalize(item.getTrangThaiMon())))
                .anyMatch(item -> !"CHO_BEP".equals(normalize(item.getTrangThaiMon())));
    }

    private String publicDeliveryStatus(String status) {
        String normalized = normalize(status);
        if (DELIVERY_AWAITING_RECONCILIATION.equals(normalized)) {
            return DELIVERY_COMPLETED;
        }
        if (LEGACY_DELIVERY_WAITING_HANDOVER.equals(normalized)) {
            return DELIVERY_WAITING_DRIVER;
        }
        return normalized;
    }

    private double positiveDoubleOrDefault(Double value, double fallback) {
        return value == null || value <= 0d ? fallback : value;
    }

    private BigDecimal configuredFee(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Phí giao hàng chưa được cấu hình hợp lệ");
        }
        return money(value);
    }

    private String inferDistrictFromAddress(String fullAddress) {
        if (!StringUtils.hasText(fullAddress)) return null;
        String normalizedAddress = normalizeLocationKey(fullAddress);
        List<String> candidates = new java.util.ArrayList<>();
        if (deliveryProperties.getInnerDistricts() != null) candidates.addAll(deliveryProperties.getInnerDistricts());
        if (deliveryProperties.getNearbyDistricts() != null) candidates.addAll(deliveryProperties.getNearbyDistricts());
        return candidates.stream()
                .filter(StringUtils::hasText)
                .filter(candidate -> normalizedAddress.contains(normalizeDistrictKey(candidate)))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesConfiguredLocation(List<String> configuredLocations, String input) {
        String key = normalizeDistrictKey(input);
        return configuredLocations != null && configuredLocations.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeDistrictKey)
                .anyMatch(key::equals);
    }

    private String normalizeDistrictKey(String value) {
        return normalizeLocationKey(value)
                .replaceFirst("^(quan|huyen)\\s+", "")
                .replaceFirst("\\s+(district|county)$", "")
                .trim();
    }

    private String normalizeLocationKey(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized
                .replaceFirst("^(thanh pho|tp|quan|huyen|thi xa)\\s+", "")
                .trim();
    }

    private String joinAddress(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(part.trim());
        }
        return result.toString();
    }

    private void assignDeliveryProvider(Order order, OrderDelivery delivery) {
        DeliveryProviderAssignment assignment = deliveryProviderService.createDelivery(order);
        if (assignment == null
                || !StringUtils.hasText(assignment.maVanDon())
                || !StringUtils.hasText(assignment.donViVanChuyen())
                || !StringUtils.hasText(assignment.tenTaiXe())
                || !StringUtils.hasText(assignment.soDienThoaiTaiXe())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Đơn vị vận chuyển chưa trả về đủ thông tin điều phối");
        }
        OrderDelivery duplicated = deliveryRepository.findByMaVanChuyen(assignment.maVanDon()).orElse(null);
        if (duplicated != null
                && (duplicated.getDonHang() == null
                        || !Objects.equals(duplicated.getDonHang().getMaDonHang(), order.getMaDonHang()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mã vận đơn do đơn vị vận chuyển trả về đã tồn tại");
        }
        delivery.setMaVanChuyen(assignment.maVanDon().trim());
        delivery.setDonViVanChuyen(assignment.donViVanChuyen().trim());
        delivery.setTenNguoiGiao(assignment.tenTaiXe().trim());
        delivery.setSoDienThoaiNguoiGiao(normalizePhone(assignment.soDienThoaiTaiXe()));
    }

    private boolean hasProviderAssignment(OrderDelivery delivery) {
        return delivery != null
                && StringUtils.hasText(delivery.getMaVanChuyen())
                && StringUtils.hasText(delivery.getDonViVanChuyen())
                && StringUtils.hasText(delivery.getTenNguoiGiao())
                && StringUtils.hasText(delivery.getSoDienThoaiNguoiGiao());
    }

    private void clearProviderAssignment(OrderDelivery delivery) {
        delivery.setMaVanChuyen(null);
        delivery.setDonViVanChuyen(null);
        delivery.setTenNguoiGiao(null);
        delivery.setSoDienThoaiNguoiGiao(null);
    }

    private void clearProviderResult(OrderDelivery delivery) {
        delivery.setTrangThaiDoiTac(null);
        delivery.setLyDoDoiTac(null);
        delivery.setNguonCapNhatDoiTac(null);
        delivery.setMaSuKienDoiTac(null);
        delivery.setThoiGianCapNhatDoiTac(null);
    }

    private String normalizeProviderStatus(String value) {
        String status = normalize(value);
        if (Set.of("DELIVERED", "SUCCESS", "THANH_CONG", PROVIDER_DELIVERED).contains(status)) {
            return PROVIDER_DELIVERED;
        }
        if (Set.of("FAILED", "FAIL", "THAT_BAI", PROVIDER_FAILED).contains(status)) {
            return PROVIDER_FAILED;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trạng thái đối tác không hợp lệ. Dùng GIAO_THANH_CONG hoặc GIAO_THAT_BAI");
    }

    private void requireAnyDeliveryStatus(OrderDelivery delivery, String... acceptedStatuses) {
        String current = normalize(delivery.getTrangThaiGiaoHang());
        for (String accepted : acceptedStatuses) {
            if (Objects.equals(accepted, current)) {
                return;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Không thể thực hiện thao tác khi đơn đang ở trạng thái " + current);
    }

    private boolean isKitchenCompleted(String status) {
        return Set.of("HOAN_THANH", "DA_HOAN_THANH", "SAN_SANG", "SAN_SANG_PHUC_VU")
                .contains(normalize(status));
    }

    private DeliveryOrderCreateResponse toCreateResponse(Order order) {
        OrderDelivery delivery = order.getGiaoHang();
        return new DeliveryOrderCreateResponse(
                order.getMaDonHang(),
                formatOrderCode(order.getMaDonHang()),
                delivery.getTrackingToken(),
                delivery.getTrangThaiGiaoHang(),
                delivery.getPhuongThucThanhToan(),
                delivery.getTrangThaiThanhToan(),
                money(order.getTamTinh()),
                money(order.getTienGiam()),
                money(delivery.getPhiGiaoHang()),
                money(order.getTongTien()),
                delivery.getThoiGianNhanMongMuon());
    }

    private DeliveryTrackingResponse toTrackingResponse(Order order) {
        OrderDelivery delivery = order.getGiaoHang();
        return new DeliveryTrackingResponse(
                order.getMaDonHang(),
                formatOrderCode(order.getMaDonHang()),
                delivery.getTrackingToken(),
                delivery.getMaVanChuyen(),
                order.getTrangThai(),
                publicDeliveryStatus(delivery.getTrangThaiGiaoHang()),
                delivery.getTenNguoiNhan(),
                maskPhone(delivery.getSoDienThoaiNhan()),
                delivery.getEmailNguoiNhan(),
                delivery.getDiaChiGiaoHang(),
                delivery.getDiaChiChiTiet(),
                delivery.getSoNha(),
                delivery.getTenDuong(),
                delivery.getPhuongXa(),
                delivery.getQuanHuyen(),
                delivery.getTinhThanh(),
                delivery.getThongTinDiaChi(),
                delivery.getKhuVucGiaoHang(),
                delivery.getGoogleMaps(),
                delivery.getGooglePlaceId(),
                delivery.getQuangDuongMet(),
                delivery.getThoiGianDuKienGiay(),
                estimatedCustomerEtaSeconds(delivery.getThoiGianDuKienGiay()),
                delivery.getGoogleRoutePolyline(),
                delivery.getGhiChuGiaoHang(),
                delivery.getLoaiThoiGianNhan(),
                delivery.getThoiGianNhanMongMuon(),
                delivery.getPhuongThucThanhToan(),
                delivery.getTrangThaiThanhToan(),
                money(delivery.getSoTienDaThanhToan()),
                money(delivery.getSoTienCanHoan()),
                money(delivery.getSoTienDaHoan()),
                delivery.getDonViVanChuyen(),
                delivery.getTenNguoiGiao(),
                maskPhone(delivery.getSoDienThoaiNguoiGiao()),
                delivery.getTrangThaiDoiTac(),
                delivery.getLyDoDoiTac(),
                money(order.getTamTinh()),
                money(order.getTienGiam()),
                money(delivery.getPhiGiaoHang()),
                money(order.getTongTien()),
                order.getThoiGianDat(),
                delivery.getThoiGianXacNhan(),
                delivery.getThoiGianHetHanThanhToan(),
                delivery.getThoiGianSanSang(),
                delivery.getThoiGianBanGiao(),
                delivery.getThoiGianCapNhatDoiTac(),
                delivery.getThoiGianGiaoThanhCong(),
                delivery.getLyDoTuChoi(),
                delivery.getLyDoGiaoThatBai(),
                groupTrackingItems(order.getChiTietDonHang()));
    }

    private List<DeliveryTrackingItemResponse> groupTrackingItems(List<OrderItem> items) {
        Map<ItemGroupKey, ItemAccumulator> grouped = new LinkedHashMap<>();
        for (OrderItem item : items) {
            ItemGroupKey key = new ItemGroupKey(
                    item.getMonAn().getMaMonAn(),
                    trimToNull(item.getGhiChu()),
                    item.getDonGia());
            ItemAccumulator accumulator = grouped.computeIfAbsent(
                    key,
                    ignored -> new ItemAccumulator(item));
            accumulator.add(item);
        }
        return grouped.values().stream().map(ItemAccumulator::toResponse).toList();
    }

    private String formatOrderCode(Integer orderId) {
        return orderId == null ? null : String.format("DH%07d", orderId);
    }

    private String normalizePhone(String value) {
        String phone = requiredText(value, "Số điện thoại không hợp lệ")
                .replaceAll("[ .()\\-]", "");
        if (!phone.matches("^\\+?[0-9]{9,15}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        return phone;
    }

    private String maskPhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String phone = value.trim();
        if (phone.length() <= 6) {
            return phone;
        }
        return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 3);
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record ItemGroupKey(Integer foodId, String note, BigDecimal unitPrice) {
    }

    private static final class ItemAccumulator {
        private final OrderItem sample;
        private int quantity;
        private int waiting;
        private int cooking;
        private int completed;
        private int cancelled;

        private ItemAccumulator(OrderItem sample) {
            this.sample = sample;
        }

        private void add(OrderItem item) {
            quantity++;
            String status = item.getTrangThaiMon() == null
                    ? ""
                    : item.getTrangThaiMon().trim().toUpperCase(Locale.ROOT);
            if ("DA_HUY".equals(status)) {
                cancelled++;
            } else if (Set.of("DANG_NAU", "DANG_CHE_BIEN").contains(status)) {
                cooking++;
            } else if (Set.of("HOAN_THANH", "DA_HOAN_THANH", "SAN_SANG", "SAN_SANG_PHUC_VU", "DA_PHUC_VU")
                    .contains(status)) {
                completed++;
            } else {
                waiting++;
            }
        }

        private DeliveryTrackingItemResponse toResponse() {
            BigDecimal unitPrice = sample.getDonGia() == null ? BigDecimal.ZERO : sample.getDonGia();
            return new DeliveryTrackingItemResponse(
                    sample.getMonAn().getMaMonAn(),
                    sample.getMonAn().getTenMonAn(),
                    sample.getMonAn().getHinhAnh(),
                    sample.getGhiChu(),
                    quantity,
                    waiting,
                    cooking,
                    completed,
                    cancelled,
                    unitPrice,
                    unitPrice.multiply(BigDecimal.valueOf(Math.max(quantity - cancelled, 0))));
        }
    }
}
