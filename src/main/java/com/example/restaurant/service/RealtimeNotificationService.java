package com.example.restaurant.service;

import com.example.restaurant.dto.realtime.RealtimeEventResponse;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.entity.ServiceRequest;
import com.example.restaurant.entity.TableReservation;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RealtimeNotificationService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeNotificationService.class);

    private static final Set<String> READY_FOR_SERVICE_STATUSES = Set.of(
            "HOAN_THANH", "DA_HOAN_THANH", "SAN_SANG", "SAN_SANG_PHUC_VU"
    );

    private final SimpMessagingTemplate messagingTemplate;
    private final FirebasePushNotificationService firebasePushNotificationService;
    private final OrderRepository orderRepository;
    private final DiningTableRepository diningTableRepository;

    public RealtimeNotificationService(SimpMessagingTemplate messagingTemplate,
                                       FirebasePushNotificationService firebasePushNotificationService,
                                       OrderRepository orderRepository,
                                       DiningTableRepository diningTableRepository) {
        this.messagingTemplate = messagingTemplate;
        this.firebasePushNotificationService = firebasePushNotificationService;
        this.orderRepository = orderRepository;
        this.diningTableRepository = diningTableRepository;
    }

    public void notifyNewOrder(Object data) {
        // Phục vụ nhận thông báo để theo dõi đơn mới; không cần xác nhận trước khi bếp chế biến.
        send("/topic/orders", "NEW_ORDER", "Có đơn hàng mới", data);
        firebasePushNotificationService.sendToChannel(
                "WAITER",
                "Có đơn hàng mới",
                "Khách vừa gửi đơn. Đơn đã được chuyển xuống bếp.",
                "/waiter/orders",
                "waiter-new-order",
                false
        );
    }

    public void notifyKitchenOrderConfirmed(Object data) {
        // Giữ tên hàm và loại sự kiện cũ để frontend hiện tại tiếp tục tương thích.
        send("/topic/kitchen", "NEW_KITCHEN_ORDER", "Có đơn hàng mới cần chế biến", data);
        firebasePushNotificationService.sendToChannel(
                "KITCHEN",
                "Bếp có đơn mới",
                "Có đơn hàng mới đang chờ bếp bắt đầu chế biến.",
                "/kitchen/orders",
                "kitchen-new-order",
                true
        );
    }

    public void notifyOrderItemsAdded(Order order, Object newItems, Integer callNumber, boolean notifyKitchen) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maDonHang", order.getMaDonHang());
        payload.put("maBan", order.getBanAn() == null ? null : order.getBanAn().getMaBan());
        payload.put("tenBan", order.getBanAn() == null ? null : order.getBanAn().getTenBan());
        payload.put("lanGoi", callNumber);
        payload.put("monMoi", newItems);
        payload.put("maCodeKhuyenMai", order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaCode());
        payload.put("tamTinh", order.getTamTinh());
        payload.put("tienGiam", order.getTienGiam());
        payload.put("tongTien", order.getTongTien());

        send("/topic/orders", "ORDER_ITEMS_ADDED", "Đơn hàng vừa được gọi thêm món", payload);
        firebasePushNotificationService.sendToChannel(
                "WAITER",
                "Khách vừa gọi thêm món",
                "Đơn #" + order.getMaDonHang() + " vừa có món gọi thêm và đã chuyển xuống bếp.",
                "/waiter/orders/" + order.getMaDonHang(),
                "waiter-order-items-added-" + order.getMaDonHang(),
                false
        );
        if (notifyKitchen) {
            send("/topic/kitchen", "NEW_KITCHEN_ITEMS", "Bếp có món gọi thêm cần chế biến", payload);
            firebasePushNotificationService.sendToChannel(
                    "KITCHEN",
                    "Bếp có món gọi thêm",
                    "Đơn #" + order.getMaDonHang() + " có món mới đang chờ chế biến.",
                    "/kitchen/orders",
                    "kitchen-new-items-" + order.getMaDonHang(),
                    true
            );
        }
        send("/topic/cashier", "ORDER_TOTAL_CHANGED", "Tổng tiền đơn hàng đã thay đổi", payload);
    }

    public void notifyOrderStatusChanged(Object data) {
        send("/topic/orders", "ORDER_STATUS_CHANGED", "Trạng thái đơn hàng đã thay đổi", data);
        send("/topic/cashier", "ORDER_STATUS_CHANGED", "Thu ngân nhận cập nhật đơn hàng", data);
    }

    public void notifyDeliveryOrderChanged(String type, String message, Order order) {
        if (order == null) {
            return;
        }
        send("/topic/delivery-orders", type, message, order);
        send("/topic/cashier/delivery-orders", type, message, order);
        // Thu ngân là đầu mối theo dõi đơn online: phát cả trên kênh công việc chung
        // để badge/trang thông báo được cập nhật ngay khi đơn thay đổi.
        send("/topic/cashier", type, message, order);

        if (Set.of(
                "DELIVERY_ORDER_WAITING_PAYMENT",
                "DELIVERY_ORDER_PENDING_CONFIRMATION",
                "DELIVERY_PAYMENT_CONFIRMED"
        ).contains(type)) {
            boolean waitingPayment = "DELIVERY_ORDER_WAITING_PAYMENT".equals(type);
            String orderCode = order.getMaDonHang() == null ? "mới" : "#DH" + order.getMaDonHang();
            firebasePushNotificationService.sendToChannel(
                    "CASHIER",
                    waitingPayment ? "Có đơn online chờ thanh toán" : "Có đơn online chờ xác nhận",
                    waitingPayment
                            ? "Đơn " + orderCode + " đang chờ khách hoàn tất VietQR."
                            : "Đơn " + orderCode + " đang chờ nhà hàng kiểm tra và xác nhận trước khi xuống bếp.",
                    "/cashier/delivery-orders",
                    "cashier-delivery-order-" + (order.getMaDonHang() == null ? "latest" : order.getMaDonHang()),
                    false
            );
        }

        if (order.getGiaoHang() != null
                && order.getGiaoHang().getTrackingToken() != null
                && !order.getGiaoHang().getTrackingToken().isBlank()) {
            send(
                    "/topic/customer/delivery/" + order.getGiaoHang().getTrackingToken(),
                    type,
                    message,
                    order
            );
        }
    }

    public void notifyOrderPricingChanged(Order order) {
        send("/topic/orders", "ORDER_PRICING_CHANGED", "Khuyến mãi hoặc tổng tiền đơn hàng đã thay đổi", order);
        send("/topic/cashier", "ORDER_PRICING_CHANGED", "Tổng thanh toán của đơn hàng đã thay đổi", order);
        notifyCustomerOrderChanged(order);
    }

    public void notifyKitchenItemStatusChanged(Object data) {
        send("/topic/kitchen", "KITCHEN_ITEM_STATUS_CHANGED", "Trạng thái món đã thay đổi", data);
        send("/topic/orders", "KITCHEN_ITEM_STATUS_CHANGED", "Món trong đơn hàng đã được cập nhật", data);
        pushReadyItemToWaiter(data);
    }

    /**
     * Phiên bản bulk: chỉ phát một sự kiện realtime cho toàn bộ lần thao tác của bếp,
     * tránh mỗi món làm KitchenBoard/KitchenLayout tải lại active/count một lần.
     * Push cho phục vụ vẫn được giữ theo từng món vừa hoàn thành.
     */
    public void notifyKitchenItemsStatusChanged(Collection<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        send("/topic/kitchen", "KITCHEN_ITEMS_STATUS_CHANGED", "Trạng thái nhiều món đã thay đổi", items);
        send("/topic/orders", "KITCHEN_ITEMS_STATUS_CHANGED", "Nhiều món trong đơn hàng đã được cập nhật", items);
        items.forEach(this::pushReadyItemToWaiter);
    }

    public void notifyOrderItemServed(Object data) {
        send("/topic/orders", "ORDER_ITEM_SERVED", "Món đã được nhân viên phục vụ mang ra bàn", data);
        send("/topic/kitchen", "ORDER_ITEM_SERVED", "Món đã được phục vụ", data);
    }

    public void notifyItemCancellationRequested(OrderItem item) {
        send("/topic/orders", "ORDER_ITEM_CANCELLATION_REQUESTED", "Có yêu cầu hủy món cần xử lý", item);
        send("/topic/kitchen", "ORDER_ITEM_CANCELLATION_REQUESTED", "Món đang chờ xử lý yêu cầu hủy", item);
        send("/topic/admin/cancellations", "ORDER_ITEM_CANCELLATION_REQUESTED", "Có yêu cầu hủy món mới", item);
    }

    public void notifyItemCancellationCompleted(OrderItem item) {
        send("/topic/orders", "ORDER_ITEM_CANCELLED", "Món trong đơn hàng đã được hủy", item);
        send("/topic/kitchen", "ORDER_ITEM_CANCELLED", "Món đã được hủy khỏi phiếu bếp", item);
        send("/topic/cashier", "ORDER_TOTAL_CHANGED", "Tổng tiền đơn hàng đã thay đổi do hủy món", item);
        send("/topic/admin/cancellations", "ORDER_ITEM_CANCELLATION_APPROVED", "Yêu cầu hủy món đã được duyệt", item);
    }

    public void notifyItemCancellationRejected(OrderItem item) {
        send("/topic/orders", "ORDER_ITEM_CANCELLATION_REJECTED", "Yêu cầu hủy món đã bị từ chối", item);
        send("/topic/kitchen", "ORDER_ITEM_CANCELLATION_REJECTED", "Món được khôi phục về trạng thái trước đó", item);
        send("/topic/admin/cancellations", "ORDER_ITEM_CANCELLATION_REJECTED", "Yêu cầu hủy món đã bị từ chối", item);
    }

    public void notifyServiceRequestCreated(ServiceRequest request) {
        send("/topic/service-requests", "SERVICE_REQUEST_CREATED", "Có yêu cầu phục vụ mới", request);
        send("/topic/admin/service-requests", "SERVICE_REQUEST_CREATED", "Có yêu cầu phục vụ mới", request);
        firebasePushNotificationService.sendToChannel(
                "WAITER",
                "Có yêu cầu phục vụ mới",
                (request.getTenBan() == null ? "Có bàn" : request.getTenBan()) + " đang chờ nhân viên tiếp nhận.",
                "/waiter/requests",
                "waiter-service-request-" + request.getMaYeuCau(),
                true
        );
        notifyCustomerServiceRequestChanged(request, "SERVICE_REQUEST_CREATED", "Yêu cầu đã được gửi");
    }

    public void notifyServiceRequestAccepted(ServiceRequest request) {
        send("/topic/service-requests", "SERVICE_REQUEST_ACCEPTED", "Yêu cầu đã được nhân viên tiếp nhận", request);
        send("/topic/admin/service-requests", "SERVICE_REQUEST_ACCEPTED", "Yêu cầu đã được nhân viên tiếp nhận", request);
        notifyCustomerServiceRequestChanged(request, "SERVICE_REQUEST_ACCEPTED", "Nhân viên đã tiếp nhận yêu cầu");
    }

    public void notifyServiceRequestCompleted(ServiceRequest request) {
        send("/topic/service-requests", "SERVICE_REQUEST_COMPLETED", "Yêu cầu phục vụ đã hoàn thành", request);
        send("/topic/admin/service-requests", "SERVICE_REQUEST_COMPLETED", "Yêu cầu phục vụ đã hoàn thành", request);
        notifyCustomerServiceRequestChanged(request, "SERVICE_REQUEST_COMPLETED", "Yêu cầu đã được xử lý");
    }

    public void notifyServiceRequestCancelled(ServiceRequest request) {
        send("/topic/service-requests", "SERVICE_REQUEST_CANCELLED", "Yêu cầu phục vụ đã bị hủy", request);
        send("/topic/admin/service-requests", "SERVICE_REQUEST_CANCELLED", "Yêu cầu phục vụ đã bị hủy", request);
        notifyCustomerServiceRequestChanged(request, "SERVICE_REQUEST_CANCELLED", "Yêu cầu phục vụ đã bị hủy");
    }

    public void notifyServiceRequestTransferred(ServiceRequest request) {
        send("/topic/service-requests", "SERVICE_REQUEST_TRANSFERRED", "Yêu cầu phục vụ đã được chuyển sang bàn mới", request);
        send("/topic/admin/service-requests", "SERVICE_REQUEST_TRANSFERRED", "Yêu cầu phục vụ đã được chuyển sang bàn mới", request);
        notifyCustomerServiceRequestChanged(request, "SERVICE_REQUEST_TRANSFERRED", "Yêu cầu đã được chuyển theo bàn mới");
    }

    private void notifyCustomerServiceRequestChanged(ServiceRequest request, String type, String message) {
        if (request == null || request.getMaBan() == null) {
            return;
        }
        send(
                "/topic/customer/tables/" + request.getMaBan() + "/service-requests",
                type,
                message,
                request
        );
        // Giữ tương thích với màn hình khách đang nghe topic chung theo bàn.
        send(
                "/topic/customer/tables/" + request.getMaBan(),
                type,
                message,
                request
        );
    }

    public void notifyReservationPreorderRequiresReapproval(TableReservation reservation) {
        notifyReservationChanged(
                "RESERVATION_PREORDER_CHANGED_AFTER_CONFIRMATION",
                "Khách đã thay đổi món đặt trước và cần được duyệt lại",
                reservation
        );
        send(
                "/topic/cashier",
                "RESERVATION_PREORDER_CHANGED_AFTER_CONFIRMATION",
                "Có thực đơn đặt trước vừa được khách thay đổi sau khi đã duyệt",
                reservation
        );
        String code = reservation == null || reservation.getMaTraCuu() == null
                ? "lịch đặt bàn"
                : reservation.getMaTraCuu();
        firebasePushNotificationService.sendToChannel(
                "CASHIER",
                "Khách vừa thay đổi món đặt trước",
                "Lịch " + code + " đã thay đổi thực đơn sau lần duyệt gần nhất. Vui lòng kiểm tra và duyệt lại.",
                "/cashier/reservations",
                "cashier-reservation-preorder-reapproval-" + (reservation == null ? "latest" : reservation.getMaDatBan()),
                true
        );
    }

    public void notifyReservationPreorderReviewRequiredAtCheckIn(TableReservation reservation) {
        boolean changedAfterConfirmation = reservation != null
                && Boolean.TRUE.equals(reservation.getCanDuyetLaiDatMonTruoc());
        String message = changedAfterConfirmation
                ? "Khách đã đến nhưng thực đơn vừa thay đổi sau lần duyệt; cần duyệt lại trước khi chuyển bếp"
                : "Khách đã đến nhưng thực đơn đặt trước vẫn chưa được duyệt";
        send(
                "/topic/cashier/reservations",
                "RESERVATION_PREORDER_REVIEW_REQUIRED_AT_CHECKIN",
                message,
                reservation
        );
        send(
                "/topic/cashier",
                "RESERVATION_PREORDER_REVIEW_REQUIRED_AT_CHECKIN",
                message,
                reservation
        );
        String code = reservation == null || reservation.getMaTraCuu() == null
                ? "lịch đặt bàn"
                : reservation.getMaTraCuu();
        firebasePushNotificationService.sendToChannel(
                "CASHIER",
                "Có thực đơn cần duyệt ngay",
                "Khách của lịch " + code + " đã check-in nhưng thực đơn đặt trước chưa được duyệt xong.",
                "/cashier/reservations",
                "cashier-reservation-preorder-checkin-" + (reservation == null ? "latest" : reservation.getMaDatBan()),
                true
        );
    }

    public void notifyReservationChanged(String type, String message, TableReservation reservation) {
        send("/topic/reservations", type, message, reservation);
        send("/topic/admin/reservations", type, message, reservation);
        send("/topic/cashier/reservations", type, message, reservation);
        if (reservation != null && reservation.getMaTraCuu() != null
                && !reservation.getMaTraCuu().isBlank()) {
            send(
                    "/topic/customer/reservations/" + reservation.getMaTraCuu(),
                    type,
                    message,
                    reservation
            );
        }
        if (reservation != null && reservation.getBanThucTe() != null
                && reservation.getBanThucTe().getMaBan() != null) {
            send(
                    "/topic/customer/tables/" + reservation.getBanThucTe().getMaBan(),
                    type,
                    message,
                    reservation
            );
        }
    }

    public void notifyPaymentCompleted(Object data) {
        send("/topic/payments", "PAYMENT_COMPLETED", "Thanh toán thành công", data);
        send("/topic/dashboard", "DASHBOARD_REFRESH", "Dashboard cần cập nhật dữ liệu mới", data);
        send("/topic/orders", "PAYMENT_COMPLETED", "Đơn hàng đã thanh toán", data);
    }

    public void notifyCustomerOrderChanged(Order order) {
        if (order == null || order.getMaDonHang() == null) {
            return;
        }

        // Bàn ghép là một phiên QR chung: thay đổi ở một đơn phải đánh thức mọi
        // màn hình đang mở bằng QR của các bàn trong nhóm để chúng tải lại cùng dữ liệu.
        LinkedHashSet<Integer> orderIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> tableIds = new LinkedHashSet<>();
        orderIds.add(order.getMaDonHang());
        if (order.getBanAn() != null && order.getBanAn().getMaBan() != null) {
            tableIds.add(order.getBanAn().getMaBan());
        }

        String groupId = order.getMaNhomThanhToan();
        if ((groupId == null || groupId.isBlank())
                && order.getBanAn() != null
                && order.getBanAn().getMaNhomBan() != null
                && !order.getBanAn().getMaNhomBan().isBlank()) {
            groupId = order.getBanAn().getMaNhomBan();
        }
        if (groupId != null && !groupId.isBlank()) {
            orderRepository.findByMaNhomThanhToanOrderByThoiGianDatAscMaDonHangAsc(groupId)
                    .forEach(groupOrder -> {
                        if (groupOrder.getMaDonHang() != null) {
                            orderIds.add(groupOrder.getMaDonHang());
                        }
                        if (groupOrder.getBanAn() != null && groupOrder.getBanAn().getMaBan() != null) {
                            tableIds.add(groupOrder.getBanAn().getMaBan());
                        }
                    });
            diningTableRepository.findByMaNhomBanOrderByMaBanAsc(groupId).stream()
                    .map(table -> table.getMaBan())
                    .filter(java.util.Objects::nonNull)
                    .forEach(tableIds::add);
        }

        for (Integer orderId : orderIds) {
            send(
                    "/topic/customer/orders/" + orderId,
                    "CUSTOMER_ORDER_UPDATED",
                    "Đơn hàng hoặc nhóm bàn của khách đã được cập nhật",
                    order
            );
        }
        for (Integer tableId : tableIds) {
            send(
                    "/topic/customer/tables/" + tableId,
                    "CUSTOMER_TABLE_ORDER_UPDATED",
                    "Phiên phục vụ tại bàn đã được cập nhật",
                    order
            );
        }

        if (order.getGiaoHang() != null
                && order.getGiaoHang().getTrackingToken() != null
                && !order.getGiaoHang().getTrackingToken().isBlank()) {
            send(
                    "/topic/customer/delivery/" + order.getGiaoHang().getTrackingToken(),
                    "CUSTOMER_DELIVERY_ORDER_UPDATED",
                    "Đơn giao hàng đã được cập nhật",
                    order
            );
        }
    }

    public void notifyMenuAvailabilityChanged(Object data) {
        send("/topic/menu", "MENU_AVAILABILITY_CHANGED", "Trạng thái phục vụ của món ăn đã thay đổi", data);
        send("/topic/kitchen", "MENU_AVAILABILITY_CHANGED", "Danh sách món ăn đã được cập nhật", data);
    }

    public void notifyDashboardRefresh(Object data) {
        send("/topic/dashboard", "DASHBOARD_REFRESH", "Dashboard cần cập nhật dữ liệu mới", data);
    }

    /** Thông báo chuyển bàn, ghép bàn hoặc tách bàn cho các màn hình liên quan. */
    public void notifyTableArrangementChanged(String type,
                                              String message,
                                              Object data,
                                              Collection<Integer> tableIds) {
        send("/topic/tables", type, message, data);
        send("/topic/orders", type, message, data);
        if (tableIds != null) {
            tableIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .forEach(tableId -> send(
                            "/topic/customer/tables/" + tableId,
                            type,
                            message,
                            data
                    ));
        }
    }

    public void notifyReviewChanged(String type, String message, Object data) {
        send("/topic/reviews", type, message, data);
    }

    public void notifyAdminInventoryAlert(Object data) {
        send(
                "/topic/admin/notifications",
                "INVENTORY_LOW_STOCK_ALERT",
                "Kho nguyên liệu có cảnh báo mới",
                data
        );
    }

    public void notifyAdminInventoryRecovered(Object data) {
        send(
                "/topic/admin/notifications",
                "INVENTORY_STOCK_RECOVERED",
                "Tồn kho nguyên liệu đã được bổ sung",
                data
        );
    }

    private void pushReadyItemToWaiter(Object data) {
        String status = null;
        String itemName = "Có món";
        Integer orderId = null;
        Integer itemId = null;

        if (data instanceof OrderItem item) {
            status = item.getTrangThaiMon();
            if (item.getMonAn() != null && item.getMonAn().getTenMonAn() != null) {
                itemName = item.getMonAn().getTenMonAn();
            }
            orderId = item.getDonHang() == null ? null : item.getDonHang().getMaDonHang();
            itemId = item.getMaChiTiet();
        } else if (data instanceof Map<?, ?> map) {
            Object rawStatus = map.get("trangThaiMon");
            if (rawStatus == null) rawStatus = map.get("trangThai");
            status = rawStatus == null ? null : String.valueOf(rawStatus);
            Object rawName = map.get("tenMonAn");
            if (rawName != null && !String.valueOf(rawName).isBlank()) itemName = String.valueOf(rawName);
            orderId = integerValue(map.get("maDonHang"));
            itemId = integerValue(map.get("maChiTiet"));
        }

        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!READY_FOR_SERVICE_STATUSES.contains(normalizedStatus)) return;

        firebasePushNotificationService.sendToChannel(
                "WAITER",
                "Món đã sẵn sàng phục vụ",
                itemName + " đã hoàn thành, cần mang ra bàn.",
                orderId == null ? "/waiter/orders" : "/waiter/orders/" + orderId,
                "waiter-ready-item-" + (itemId == null ? "latest" : itemId),
                true
        );
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * WebSocket chỉ là kênh thông báo bổ trợ. Lỗi chuyển đổi/gửi realtime không được làm
     * rollback thao tác nghiệp vụ đã cập nhật thành công trong cơ sở dữ liệu.
     */
    private void send(String destination, String type, String message, Object data) {
        try {
            Object safeData = toSafePayload(data);
            messagingTemplate.convertAndSend(
                    destination,
                    RealtimeEventResponse.of(type, message, safeData)
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Không thể gửi thông báo realtime. destination={}, type={}",
                    destination,
                    type,
                    ex
            );
        }
    }

    /**
     * Không gửi trực tiếp entity JPA qua WebSocket vì Hibernate proxy hoặc quan hệ entity
     * có thể làm Jackson lỗi khi serialize. Frontend hiện chỉ cần sự kiện để tải lại dữ liệu,
     * nên payload gọn này đã đủ và ổn định hơn.
     */
    private Object toSafePayload(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Order order) {
            return toOrderPayload(order);
        }
        if (data instanceof OrderItem item) {
            return toOrderItemPayload(item);
        }
        if (data instanceof ServiceRequest request) {
            return toServiceRequestPayload(request);
        }
        if (data instanceof TableReservation reservation) {
            return toReservationPayload(reservation);
        }
        if (data instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), toSafePayload(value)));
            return result;
        }
        if (data instanceof Iterable<?> source) {
            List<Object> result = new ArrayList<>();
            source.forEach(value -> result.add(toSafePayload(value)));
            return result;
        }
        if (data.getClass().isArray() && data instanceof Object[] source) {
            List<Object> result = new ArrayList<>(source.length);
            for (Object value : source) {
                result.add(toSafePayload(value));
            }
            return result;
        }
        return data;
    }

    private Map<String, Object> toOrderPayload(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maDonHang", order.getMaDonHang());
        payload.put("maBan", order.getBanAn() == null ? null : order.getBanAn().getMaBan());
        payload.put("tenBan", order.getBanAn() == null ? null : order.getBanAn().getTenBan());
        payload.put("trangThai", order.getTrangThai());
        payload.put("loaiDon", order.getLoaiDon());
        payload.put("nguonDon", order.getNguonDon());
        payload.put("maVanChuyen", order.getGiaoHang() == null ? null : order.getGiaoHang().getMaVanChuyen());
        payload.put("trangThaiGiaoHang", order.getGiaoHang() == null ? null : order.getGiaoHang().getTrangThaiGiaoHang());
        payload.put("tenNguoiNhan", order.getGiaoHang() == null ? null : order.getGiaoHang().getTenNguoiNhan());
        payload.put("phiGiaoHang", order.getGiaoHang() == null ? null : order.getGiaoHang().getPhiGiaoHang());
        payload.put("maCodeKhuyenMai", order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaCode());
        payload.put("tamTinh", order.getTamTinh());
        payload.put("tienGiam", order.getTienGiam());
        payload.put("tongTien", order.getTongTien());
        payload.put("thoiGianDat", order.getThoiGianDat());
        payload.put("thoiGianCapNhat", order.getThoiGianCapNhat());
        payload.put("thoiGianSanSang", order.getThoiGianSanSang());
        payload.put("thoiGianYeuCauThanhToan", order.getThoiGianYeuCauThanhToan());
        return payload;
    }

    private Map<String, Object> toOrderItemPayload(OrderItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maChiTiet", item.getMaChiTiet());
        payload.put("maDonHang", item.getDonHang() == null ? null : item.getDonHang().getMaDonHang());
        payload.put("maMonAn", item.getMonAn() == null ? null : item.getMonAn().getMaMonAn());
        payload.put("tenMonAn", item.getMonAn() == null ? null : item.getMonAn().getTenMonAn());
        payload.put("soLuong", item.getSoLuong());
        payload.put("trangThaiMon", item.getTrangThaiMon());
        payload.put("trangThaiHuy", item.getTrangThaiHuy());
        payload.put("trangThaiTruocHuy", item.getTrangThaiTruocHuy());
        payload.put("maLyDoHuy", item.getMaLyDoHuy());
        payload.put("lyDoHuy", item.getLyDoHuy());
        payload.put("ghiChuHuy", item.getGhiChuHuy());
        payload.put("nguonYeuCauHuy", item.getNguonYeuCauHuy());
        payload.put("thoiGianYeuCauHuy", item.getThoiGianYeuCauHuy());
        payload.put("thoiGianXuLyHuy", item.getThoiGianXuLyHuy());
        payload.put("lanGoi", item.getLanGoi());
        payload.put("thoiGianThem", item.getThoiGianThem());
        return payload;
    }

    private Map<String, Object> toReservationPayload(TableReservation reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maDatBan", reservation.getMaDatBan());
        payload.put("maTraCuu", reservation.getMaTraCuu());
        payload.put("ngayGioDen", reservation.getNgayGioDen());
        payload.put("thoiGianKetThucDuKien", reservation.getThoiGianKetThucDuKien());
        payload.put("soLuongKhach", reservation.getSoLuongKhach());
        payload.put("khuVucMongMuon", reservation.getKhuVucMongMuon());
        payload.put("maBanDuKien", reservation.getBanDuKien() == null ? null : reservation.getBanDuKien().getMaBan());
        payload.put("tenBanDuKien", reservation.getBanDuKien() == null ? null : reservation.getBanDuKien().getTenBan());
        payload.put("maBanThucTe", reservation.getBanThucTe() == null ? null : reservation.getBanThucTe().getMaBan());
        payload.put("tenBanThucTe", reservation.getBanThucTe() == null ? null : reservation.getBanThucTe().getTenBan());
        payload.put("maDonHang", reservation.getDonHang() == null ? null : reservation.getDonHang().getMaDonHang());
        payload.put("trangThai", reservation.getTrangThai());
        payload.put("ghiChu", reservation.getGhiChu());
        payload.put("lyDoHuyTuChoi", reservation.getLyDoHuyTuChoi());
        payload.put("thoiGianTao", reservation.getThoiGianTao());
        payload.put("thoiGianXacNhan", reservation.getThoiGianXacNhan());
        payload.put("thoiGianCheckIn", reservation.getThoiGianCheckIn());
        payload.put("thoiGianXepBan", reservation.getThoiGianXepBan());
        payload.put("thoiGianHoanThanh", reservation.getThoiGianHoanThanh());
        payload.put("trangThaiDatMonTruoc", reservation.getTrangThaiDatMonTruoc());
        payload.put("thoiGianDatMonTruoc", reservation.getThoiGianDatMonTruoc());
        payload.put("thoiGianXacNhanMonTruoc", reservation.getThoiGianXacNhanMonTruoc());
        payload.put("thoiGianDuKienChuyenBep", reservation.getThoiGianDuKienChuyenBep());
        payload.put("thoiGianChuyenBep", reservation.getThoiGianChuyenBep());
        payload.put("canDuyetLaiDatMonTruoc", Boolean.TRUE.equals(reservation.getCanDuyetLaiDatMonTruoc()));
        payload.put("thoiGianThayDoiDatMonTruoc", reservation.getThoiGianThayDoiDatMonTruoc());
        return payload;
    }

    private Map<String, Object> toServiceRequestPayload(ServiceRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maYeuCau", request.getMaYeuCau());
        payload.put("maBan", request.getMaBan());
        payload.put("tenBan", request.getTenBan());
        payload.put("khuVuc", request.getKhuVuc());
        payload.put("loaiYeuCau", request.getLoaiYeuCau());
        payload.put("noiDung", request.getNoiDung());
        payload.put("trangThai", request.getTrangThai());
        payload.put("mucDoUuTien", request.getMucDoUuTien());
        payload.put("maNhanVienTiepNhan", request.getMaNhanVienTiepNhan());
        payload.put("tenNhanVienTiepNhan", request.getTenNhanVienTiepNhan());
        payload.put("thoiGianTao", request.getThoiGianTao());
        payload.put("thoiGianTiepNhan", request.getThoiGianTiepNhan());
        payload.put("thoiGianHoanThanh", request.getThoiGianHoanThanh());
        payload.put("thoiGianHuy", request.getThoiGianHuy());
        payload.put("nguonHuy", request.getNguonHuy());
        payload.put("lyDoHuy", request.getLyDoHuy());
        return payload;
    }
}
