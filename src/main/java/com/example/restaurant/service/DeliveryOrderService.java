package com.example.restaurant.service;

import com.example.restaurant.config.DeliveryProperties;
import com.example.restaurant.dto.DeliveryHandoverRequest;
import com.example.restaurant.dto.DeliveryOrderCreateRequest;
import com.example.restaurant.dto.DeliveryOrderCreateResponse;
import com.example.restaurant.dto.DeliveryPaymentConfirmRequest;
import com.example.restaurant.dto.DeliveryReasonRequest;
import com.example.restaurant.dto.DeliveryTrackingItemResponse;
import com.example.restaurant.dto.DeliveryTrackingResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.entity.Food;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderDelivery;
import com.example.restaurant.entity.OrderItem;
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
import java.time.LocalDateTime;
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
    private static final String PAYMENT_COD = "COD";
    private static final String PAYMENT_VIETQR = "VIETQR";
    private static final Set<String> PAYMENT_METHODS = Set.of(PAYMENT_COD, PAYMENT_VIETQR);

    private static final String DELIVERY_WAITING_CONFIRMATION = "CHO_XAC_NHAN";
    private static final String DELIVERY_PREPARING = "DANG_CHUAN_BI";
    private static final String DELIVERY_WAITING_HANDOVER = "CHO_BAN_GIAO";
    private static final String DELIVERY_IN_TRANSIT = "DANG_GIAO";
    private static final String DELIVERY_COMPLETED = "HOAN_THANH";
    private static final String DELIVERY_FAILED = "GIAO_THAT_BAI";
    private static final String DELIVERY_CANCELLED = "DA_HUY";

    private static final String PAYMENT_WAITING = "CHO_THANH_TOAN";
    private static final String PAYMENT_PAID = "DA_THANH_TOAN";
    private static final String PAYMENT_REFUND_PENDING = "CHO_HOAN_TIEN";

    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final FoodRepository foodRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrderPricingService orderPricingService;
    private final PaymentService paymentService;
    private final DeliveryProperties deliveryProperties;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;

    public DeliveryOrderService(OrderRepository orderRepository,
                                OrderDeliveryRepository deliveryRepository,
                                FoodRepository foodRepository,
                                InvoiceRepository invoiceRepository,
                                OrderPricingService orderPricingService,
                                PaymentService paymentService,
                                DeliveryProperties deliveryProperties,
                                RealtimeNotificationService realtimeNotificationService,
                                SystemActivityService systemActivityService) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.foodRepository = foodRepository;
        this.invoiceRepository = invoiceRepository;
        this.orderPricingService = orderPricingService;
        this.paymentService = paymentService;
        this.deliveryProperties = deliveryProperties;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional
    public DeliveryOrderCreateResponse createOrder(DeliveryOrderCreateRequest request) {
        String clientRequestId = requiredText(request.clientRequestId(), "Mã chống tạo trùng không hợp lệ");
        String recipientPhone = normalizePhone(request.soDienThoaiNhan());
        OrderDelivery duplicated = deliveryRepository.findByClientRequestId(clientRequestId).orElse(null);
        if (duplicated != null) {
            if (!Objects.equals(recipientPhone, duplicated.getSoDienThoaiNhan())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Mã chống tạo trùng đã được sử dụng cho một đơn khác"
                );
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
                    "Mỗi món tối đa " + maxPerItem + " suất và một đơn tối đa " + maxPerOrder + " suất"
            );
        }

        String paymentMethod = normalize(request.phuongThucThanhToan());
        if (!PAYMENT_METHODS.contains(paymentMethod)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ hỗ trợ hai phương thức thanh toán: COD và VIETQR"
            );
        }

        String deliveryArea = normalize(request.khuVucGiaoHang());
        BigDecimal deliveryFee = resolveDeliveryFee(deliveryArea);

        Order order = new Order();
        order.setBanAn(null);
        order.setLoaiDon(ORDER_TYPE_DELIVERY);
        order.setNguonDon(ORDER_SOURCE_WEBSITE);
        order.setTrangThai(DELIVERY_WAITING_CONFIRMATION);
        order.setGhiChu(trimToNull(request.ghiChuDonHang()));
        order.setTamTinh(BigDecimal.ZERO);
        order.setTienGiam(BigDecimal.ZERO);
        order.setTongTien(BigDecimal.ZERO);

        LocalDateTime addedAt = LocalDateTime.now();
        for (var requestedItem : request.items()) {
            Food food = foodRepository.findById(requestedItem.maMonAn())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy món ăn: " + requestedItem.maMonAn()
                    ));
            if (!Boolean.TRUE.equals(food.getTrangThai())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Món ăn đang ngừng bán: " + food.getTenMonAn()
                );
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
        delivery.setDiaChiGiaoHang(requiredText(request.diaChiGiaoHang(), "Địa chỉ giao hàng không hợp lệ"));
        delivery.setKhuVucGiaoHang(deliveryArea);
        delivery.setGhiChuGiaoHang(trimToNull(request.ghiChuGiaoHang()));
        delivery.setPhiGiaoHang(deliveryFee);
        delivery.setPhuongThucThanhToan(paymentMethod);
        delivery.setTrangThaiThanhToan(PAYMENT_WAITING);
        delivery.setTrangThaiGiaoHang(DELIVERY_WAITING_CONFIRMATION);
        order.setGiaoHang(delivery);

        orderPricingService.recalculate(order);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_ORDER_CREATED",
                "Đơn giao hàng #DH" + savedOrder.getMaDonHang() + " đã được khách gửi và đang chờ xác nhận",
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_ORDER_CREATED",
                "Có đơn giao hàng mới chờ xác nhận",
                savedOrder
        );
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
                        "Không tìm thấy đơn giao hàng: " + orderId
                )));
    }

    @Transactional(readOnly = true)
    public DeliveryTrackingResponse track(String trackingToken) {
        OrderDelivery delivery = deliveryRepository.findByTrackingToken(requiredText(
                        trackingToken,
                        "Mã tra cứu không hợp lệ"
                ))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng theo mã tra cứu"
                ));
        return toTrackingResponse(delivery.getDonHang());
    }

    @Transactional(readOnly = true)
    public VietQrResponse createVietQr(String trackingToken) {
        OrderDelivery delivery = deliveryRepository.findByTrackingToken(requiredText(
                        trackingToken,
                        "Mã tra cứu không hợp lệ"
                ))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng theo mã tra cứu"
                ));
        if (!PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng không sử dụng phương thức VietQR"
            );
        }
        if (PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã được xác nhận thanh toán");
        }
        if (Set.of(DELIVERY_CANCELLED, DELIVERY_COMPLETED)
                .contains(normalize(delivery.getTrangThaiGiaoHang()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã kết thúc");
        }
        return paymentService.createVietQrForDelivery(delivery.getDonHang().getMaDonHang());
    }

    @Transactional
    public Order cancelByCustomer(String trackingToken, DeliveryReasonRequest request) {
        OrderDelivery delivery = deliveryRepository.findByTrackingToken(requiredText(
                        trackingToken,
                        "Mã tra cứu không hợp lệ"
                ))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn giao hàng theo mã tra cứu"
                ));
        Order order = lockOrder(delivery.getDonHang().getMaDonHang());
        return cancelWaitingOrder(order, request.lyDo(), "Khách hàng đã hủy đơn trước khi nhà hàng xác nhận");
    }

    @Transactional
    public Order confirm(Integer orderId) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_WAITING_CONFIRMATION);

        if (PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))
                && !PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn VietQR chỉ được xác nhận sau khi thu ngân kiểm tra và xác nhận đã nhận tiền"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
        delivery.setThoiGianXacNhan(now);
        delivery.setLyDoTuChoi(null);
        order.setTrangThai("DA_XAC_NHAN");
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_ORDER_CONFIRMED",
                "Đơn giao hàng #DH" + savedOrder.getMaDonHang() + " đã được xác nhận và chuyển xuống bếp",
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyKitchenOrderConfirmed(savedOrder);
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_ORDER_CONFIRMED",
                "Đơn giao hàng đã được nhà hàng xác nhận",
                savedOrder
        );
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order reject(Integer orderId, DeliveryReasonRequest request) {
        Order order = lockDeliveryOrder(orderId);
        return cancelWaitingOrder(order, request.lyDo(), "Nhà hàng đã từ chối đơn giao hàng");
    }

    @Transactional
    public Order confirmVietQrPayment(Integer orderId, DeliveryPaymentConfirmRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        if (!PAYMENT_VIETQR.equals(normalize(delivery.getPhuongThucThanhToan()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không sử dụng VietQR");
        }
        if (DELIVERY_CANCELLED.equals(normalize(delivery.getTrangThaiGiaoHang()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã hủy");
        }
        if (PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            return order;
        }

        String transactionCode = requiredText(request.maGiaoDich(), "Mã giao dịch không hợp lệ");
        if (deliveryRepository.existsByMaGiaoDichIgnoreCase(transactionCode)
                || invoiceRepository.existsByMaGiaoDichIgnoreCase(transactionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
        }
        orderPricingService.recalculate(order);
        delivery.setMaGiaoDich(transactionCode);
        delivery.setTrangThaiThanhToan(PAYMENT_PAID);
        delivery.setSoTienDaThanhToan(money(order.getTongTien()));
        delivery.setGhiChuThanhToan(trimToNull(request.ghiChu()));
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_VIETQR_CONFIRMED",
                "Thu ngân đã xác nhận VietQR cho đơn giao hàng #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_PAYMENT_CONFIRMED",
                "Thanh toán VietQR của đơn giao hàng đã được xác nhận",
                savedOrder
        );
        return savedOrder;
    }

    /**
     * Được OrderService gọi sau mỗi lần bếp cập nhật một suất món.
     * Khi toàn bộ suất hợp lệ hoàn thành, hệ thống tự sinh mã vận chuyển đúng một lần.
     */
    @Transactional
    public void synchronizeAfterKitchenUpdate(Order order) {
        if (!isDeliveryOrder(order) || order.getGiaoHang() == null) {
            return;
        }
        OrderDelivery delivery = order.getGiaoHang();
        String deliveryStatus = normalize(delivery.getTrangThaiGiaoHang());
        if (Set.of(
                DELIVERY_WAITING_CONFIRMATION,
                DELIVERY_IN_TRANSIT,
                DELIVERY_COMPLETED,
                DELIVERY_FAILED,
                DELIVERY_CANCELLED
        ).contains(deliveryStatus)) {
            return;
        }

        boolean hasPendingCancellation = order.getChiTietDonHang().stream()
                .anyMatch(item -> "YEU_CAU_HUY".equals(normalize(item.getTrangThaiMon())));
        if (hasPendingCancellation) {
            delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
            delivery.setMaVanChuyen(null);
            delivery.setThoiGianSanSang(null);
            order.setThoiGianSanSang(null);
            order.setTrangThai("DANG_CHE_BIEN");
            return;
        }

        List<OrderItem> activeItems = order.getChiTietDonHang().stream()
                .filter(item -> !"DA_HUY".equals(normalize(item.getTrangThaiMon())))
                .toList();
        if (activeItems.isEmpty()) {
            if ("DA_HUY".equals(normalize(order.getTrangThai()))) {
                delivery.setTrangThaiGiaoHang(DELIVERY_CANCELLED);
                delivery.setThoiGianHuy(delivery.getThoiGianHuy() == null
                        ? LocalDateTime.now()
                        : delivery.getThoiGianHuy());
                delivery.setMaVanChuyen(null);
                delivery.setThoiGianSanSang(null);
                order.setThoiGianSanSang(null);
                if (PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
                    delivery.setTrangThaiThanhToan(PAYMENT_REFUND_PENDING);
                }
            }
            return;
        }

        boolean allCompleted = activeItems.stream()
                .allMatch(item -> isKitchenCompleted(item.getTrangThaiMon()));
        if (allCompleted) {
            if (!StringUtils.hasText(delivery.getMaVanChuyen())) {
                delivery.setMaVanChuyen(generateShippingCode(order.getMaDonHang()));
            }
            LocalDateTime readyAt = delivery.getThoiGianSanSang();
            if (readyAt == null) {
                readyAt = LocalDateTime.now();
                delivery.setThoiGianSanSang(readyAt);
            }
            if (order.getThoiGianSanSang() == null) {
                order.setThoiGianSanSang(readyAt);
            }
            delivery.setTrangThaiGiaoHang(DELIVERY_WAITING_HANDOVER);
            order.setTrangThai(DELIVERY_WAITING_HANDOVER);
            realtimeNotificationService.notifyDeliveryOrderChanged(
                    "DELIVERY_READY_FOR_HANDOVER",
                    "Đơn đã hoàn thành món và có mã vận chuyển",
                    order
            );
            return;
        }

        if (DELIVERY_WAITING_HANDOVER.equals(deliveryStatus)) {
            delivery.setMaVanChuyen(null);
            delivery.setThoiGianSanSang(null);
        }
        order.setThoiGianSanSang(null);
        delivery.setTrangThaiGiaoHang(DELIVERY_PREPARING);
        boolean anyStarted = activeItems.stream()
                .anyMatch(item -> !"CHO_BEP".equals(normalize(item.getTrangThaiMon())));
        order.setTrangThai(anyStarted ? "DANG_CHE_BIEN" : "DA_XAC_NHAN");
    }

    @Transactional
    public Order handover(Integer orderId, DeliveryHandoverRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_WAITING_HANDOVER);
        if (!StringUtils.hasText(delivery.getMaVanChuyen())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn chưa có mã vận chuyển. Bếp phải hoàn thành toàn bộ món trước khi bàn giao"
            );
        }

        delivery.setDonViVanChuyen(requiredText(request.donViVanChuyen(), "Đơn vị vận chuyển không hợp lệ"));
        delivery.setTenNguoiGiao(requiredText(request.tenNguoiGiao(), "Tên người giao không hợp lệ"));
        delivery.setSoDienThoaiNguoiGiao(normalizePhone(request.soDienThoaiNguoiGiao()));
        delivery.setGhiChuBanGiao(trimToNull(request.ghiChuBanGiao()));
        delivery.setTrangThaiGiaoHang(DELIVERY_IN_TRANSIT);
        delivery.setThoiGianBanGiao(LocalDateTime.now());
        delivery.setLyDoGiaoThatBai(null);
        order.setTrangThai(DELIVERY_IN_TRANSIT);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_HANDED_OVER",
                "Đơn " + delivery.getMaVanChuyen() + " đã bàn giao cho " + delivery.getDonViVanChuyen(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_IN_TRANSIT",
                "Đơn hàng đã được bàn giao cho người giao",
                savedOrder
        );
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order complete(Integer orderId, String username) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_IN_TRANSIT);

        String paymentMethod = normalize(delivery.getPhuongThucThanhToan());
        if (PAYMENT_VIETQR.equals(paymentMethod)
                && !PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chưa thể hoàn tất vì thanh toán VietQR chưa được thu ngân xác nhận"
            );
        }
        if (PAYMENT_COD.equals(paymentMethod)) {
            delivery.setTrangThaiThanhToan(PAYMENT_PAID);
            delivery.setSoTienDaThanhToan(money(order.getTongTien()));
        }

        delivery.setTrangThaiGiaoHang(DELIVERY_COMPLETED);
        delivery.setThoiGianGiaoThanhCong(LocalDateTime.now());
        paymentService.completeDeliveryPayment(order, username);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_COMPLETED",
                "Đơn " + delivery.getMaVanChuyen() + " đã giao thành công",
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_COMPLETED",
                "Đơn hàng đã giao thành công",
                savedOrder
        );
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order fail(Integer orderId, DeliveryReasonRequest request) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_IN_TRANSIT);

        delivery.setTrangThaiGiaoHang(DELIVERY_FAILED);
        delivery.setLyDoGiaoThatBai(requiredText(request.lyDo(), "Vui lòng nhập lý do giao thất bại"));
        order.setTrangThai(DELIVERY_FAILED);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_FAILED",
                "Đơn " + delivery.getMaVanChuyen() + " giao thất bại: " + delivery.getLyDoGiaoThatBai(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_FAILED",
                "Đơn hàng giao không thành công",
                savedOrder
        );
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    @Transactional
    public Order retry(Integer orderId) {
        Order order = lockDeliveryOrder(orderId);
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_FAILED);

        delivery.setTrangThaiGiaoHang(DELIVERY_WAITING_HANDOVER);
        delivery.setDonViVanChuyen(null);
        delivery.setTenNguoiGiao(null);
        delivery.setSoDienThoaiNguoiGiao(null);
        delivery.setGhiChuBanGiao(null);
        delivery.setThoiGianBanGiao(null);
        delivery.setLyDoGiaoThatBai(null);
        order.setTrangThai(DELIVERY_WAITING_HANDOVER);
        Order savedOrder = orderRepository.saveAndFlush(order);

        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_RETRY_READY",
                "Đơn hàng đã được đưa về chờ bàn giao lại",
                savedOrder
        );
        return savedOrder;
    }

    private Order cancelWaitingOrder(Order order, String reason, String activityMessage) {
        OrderDelivery delivery = order.getGiaoHang();
        requireDeliveryStatus(delivery, DELIVERY_WAITING_CONFIRMATION);

        order.getChiTietDonHang().forEach(item -> item.setTrangThaiMon("DA_HUY"));
        order.setTrangThai(DELIVERY_CANCELLED);
        delivery.setTrangThaiGiaoHang(DELIVERY_CANCELLED);
        delivery.setLyDoTuChoi(requiredText(reason, "Vui lòng nhập lý do hủy hoặc từ chối"));
        delivery.setThoiGianHuy(LocalDateTime.now());
        if (PAYMENT_PAID.equals(normalize(delivery.getTrangThaiThanhToan()))) {
            delivery.setTrangThaiThanhToan(PAYMENT_REFUND_PENDING);
        }
        orderPricingService.recalculate(order);
        Order savedOrder = orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_ORDER_CANCELLED",
                activityMessage + " #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyDeliveryOrderChanged(
                "DELIVERY_ORDER_CANCELLED",
                "Đơn giao hàng đã bị hủy",
                savedOrder
        );
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        return savedOrder;
    }

    private Order lockDeliveryOrder(Integer orderId) {
        return requireDeliveryOrder(lockOrder(orderId));
    }

    private Order lockOrder(Integer orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));
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
                    "Không thể thực hiện thao tác khi đơn đang ở trạng thái " + current
            );
        }
    }

    private BigDecimal resolveDeliveryFee(String area) {
        BigDecimal configuredFee = switch (area) {
            case AREA_INNER -> deliveryProperties.getInnerAreaFee();
            case AREA_NEARBY -> deliveryProperties.getNearbyAreaFee();
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khu vực giao hàng không được hỗ trợ. Dùng NOI_THANH hoặc LAN_CAN"
            );
        };
        if (configuredFee == null || configuredFee.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Phí giao hàng chưa được cấu hình hợp lệ"
            );
        }
        return configuredFee.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateShippingCode(Integer orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng chưa có mã để tạo mã vận chuyển");
        }
        String prefix = StringUtils.hasText(deliveryProperties.getShippingCodePrefix())
                ? deliveryProperties.getShippingCodePrefix().trim().toUpperCase(Locale.ROOT)
                : "LUM-VC";
        prefix = prefix.replaceAll("[^A-Z0-9-]", "");
        if (prefix.isBlank()) {
            prefix = "LUM-VC";
        }
        String code = String.format("%s-%08d", prefix, orderId);
        OrderDelivery existing = deliveryRepository.findByMaVanChuyen(code).orElse(null);
        if (existing == null
                || existing.getDonHang() == null
                || Objects.equals(existing.getDonHang().getMaDonHang(), orderId)) {
            return code;
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Mã vận chuyển đã tồn tại cho một đơn khác. Vui lòng kiểm tra dữ liệu"
        );
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
                money(order.getTongTien())
        );
    }

    private DeliveryTrackingResponse toTrackingResponse(Order order) {
        OrderDelivery delivery = order.getGiaoHang();
        return new DeliveryTrackingResponse(
                order.getMaDonHang(),
                formatOrderCode(order.getMaDonHang()),
                delivery.getTrackingToken(),
                delivery.getMaVanChuyen(),
                order.getTrangThai(),
                delivery.getTrangThaiGiaoHang(),
                delivery.getTenNguoiNhan(),
                maskPhone(delivery.getSoDienThoaiNhan()),
                delivery.getDiaChiGiaoHang(),
                delivery.getKhuVucGiaoHang(),
                delivery.getGhiChuGiaoHang(),
                delivery.getPhuongThucThanhToan(),
                delivery.getTrangThaiThanhToan(),
                money(delivery.getSoTienDaThanhToan()),
                delivery.getDonViVanChuyen(),
                delivery.getTenNguoiGiao(),
                maskPhone(delivery.getSoDienThoaiNguoiGiao()),
                money(order.getTamTinh()),
                money(order.getTienGiam()),
                money(delivery.getPhiGiaoHang()),
                money(order.getTongTien()),
                order.getThoiGianDat(),
                delivery.getThoiGianXacNhan(),
                delivery.getThoiGianSanSang(),
                delivery.getThoiGianBanGiao(),
                delivery.getThoiGianGiaoThanhCong(),
                delivery.getLyDoTuChoi(),
                delivery.getLyDoGiaoThatBai(),
                groupTrackingItems(order.getChiTietDonHang())
        );
    }

    private List<DeliveryTrackingItemResponse> groupTrackingItems(List<OrderItem> items) {
        Map<ItemGroupKey, ItemAccumulator> grouped = new LinkedHashMap<>();
        for (OrderItem item : items) {
            ItemGroupKey key = new ItemGroupKey(
                    item.getMonAn().getMaMonAn(),
                    trimToNull(item.getGhiChu()),
                    item.getDonGia()
            );
            ItemAccumulator accumulator = grouped.computeIfAbsent(
                    key,
                    ignored -> new ItemAccumulator(item)
            );
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

    private String mergeNotes(String current, String added) {
        String next = trimToNull(added);
        if (next == null) {
            return current;
        }
        if (!StringUtils.hasText(current)) {
            return limit(next, 500);
        }
        if (current.trim().equalsIgnoreCase(next)) {
            return current;
        }
        return limit(current.trim() + "; " + next, 500);
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
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
                    unitPrice.multiply(BigDecimal.valueOf(Math.max(quantity - cancelled, 0)))
            );
        }
    }
}
