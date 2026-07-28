package com.example.restaurant.service;

import com.example.restaurant.dto.realtime.RealtimeEventResponse;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.entity.ServiceRequest;
import com.example.restaurant.entity.TableReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RealtimeNotificationService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyNewOrder(Object data) {
        // Đơn mới chỉ gửi cho phục vụ. Bếp chỉ nhận sau khi phục vụ xác nhận.
        send("/topic/orders", "NEW_ORDER", "Có đơn hàng mới", data);
    }

    public void notifyKitchenOrderConfirmed(Object data) {
        send("/topic/kitchen", "NEW_KITCHEN_ORDER", "Đơn đã được xác nhận và chuyển tới bếp", data);
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
        if (notifyKitchen) {
            send("/topic/kitchen", "NEW_KITCHEN_ITEMS", "Bếp có món gọi thêm cần chế biến", payload);
        }
        send("/topic/cashier", "ORDER_TOTAL_CHANGED", "Tổng tiền đơn hàng đã thay đổi", payload);
    }

    public void notifyOrderStatusChanged(Object data) {
        send("/topic/orders", "ORDER_STATUS_CHANGED", "Trạng thái đơn hàng đã thay đổi", data);
        send("/topic/cashier", "ORDER_STATUS_CHANGED", "Thu ngân nhận cập nhật đơn hàng", data);
    }

    public void notifyOrderPricingChanged(Order order) {
        send("/topic/orders", "ORDER_PRICING_CHANGED", "Khuyến mãi hoặc tổng tiền đơn hàng đã thay đổi", order);
        send("/topic/cashier", "ORDER_PRICING_CHANGED", "Tổng thanh toán của đơn hàng đã thay đổi", order);
        notifyCustomerOrderChanged(order);
    }

    public void notifyKitchenItemStatusChanged(Object data) {
        send("/topic/kitchen", "KITCHEN_ITEM_STATUS_CHANGED", "Trạng thái món đã thay đổi", data);
        send("/topic/orders", "KITCHEN_ITEM_STATUS_CHANGED", "Món trong đơn hàng đã được cập nhật", data);
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

    public void notifyReservationChanged(String type, String message, TableReservation reservation) {
        send("/topic/reservations", type, message, reservation);
        send("/topic/admin/reservations", type, message, reservation);
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
        send(
                "/topic/customer/orders/" + order.getMaDonHang(),
                "CUSTOMER_ORDER_UPDATED",
                "Đơn hàng của khách đã được cập nhật",
                order
        );
        if (order.getBanAn() != null && order.getBanAn().getMaBan() != null) {
            send(
                    "/topic/customer/tables/" + order.getBanAn().getMaBan(),
                    "CUSTOMER_TABLE_ORDER_UPDATED",
                    "Đơn hàng tại bàn đã được cập nhật",
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
