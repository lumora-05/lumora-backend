package com.example.restaurant.service;

import com.example.restaurant.dto.ReservationCancelRequest;
import com.example.restaurant.dto.ReservationPreorderConfirmRequest;
import com.example.restaurant.dto.ReservationPreorderRequest;
import com.example.restaurant.dto.ReservationPreorderResponse;
import com.example.restaurant.entity.*;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.FoodRepository;
import com.example.restaurant.repository.OrderRepository;
import com.example.restaurant.repository.TableReservationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ReservationPreorderService {
    private static final String RESERVATION_CONFIRMED = "DA_XAC_NHAN";
    private static final String RESERVATION_ARRIVED = "KHACH_DA_DEN";
    private static final String RESERVATION_SEATED = "DA_XEP_BAN";

    private static final String PREORDER_NONE = "CHUA_DAT";
    private static final String PREORDER_PENDING = "CHO_XAC_NHAN";
    private static final String PREORDER_CONFIRMED = "DA_XAC_NHAN";
    private static final String PREORDER_REJECTED = "TU_CHOI";
    private static final String PREORDER_SENT = "DA_CHUYEN_BEP";
    private static final String PREORDER_CANCELLED = "DA_HUY";

    private static final int DEFAULT_PREPARATION_MINUTES = 30;
    private static final Set<String> OPEN_ORDER_STATUSES = Set.of(
            "CHO_XAC_NHAN", "DA_XAC_NHAN", "DANG_CHUAN_BI", "DANG_CHE_BIEN",
            "SAN_SANG", "SAN_SANG_PHUC_VU", "DA_HOAN_THANH", "DA_PHUC_VU",
            "CHO_THANH_TOAN", "SAN_SANG_THANH_TOAN"
    );

    private final TableReservationRepository reservationRepository;
    private final DiningTableRepository diningTableRepository;
    private final FoodRepository foodRepository;
    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final OrderPricingService orderPricingService;
    private final ReservationService reservationService;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;

    public ReservationPreorderService(TableReservationRepository reservationRepository,
                                      DiningTableRepository diningTableRepository,
                                      FoodRepository foodRepository,
                                      OrderRepository orderRepository,
                                      EmployeeRepository employeeRepository,
                                      OrderPricingService orderPricingService,
                                      ReservationService reservationService,
                                      RealtimeNotificationService realtimeNotificationService,
                                      SystemActivityService systemActivityService) {
        this.reservationRepository = reservationRepository;
        this.diningTableRepository = diningTableRepository;
        this.foodRepository = foodRepository;
        this.orderRepository = orderRepository;
        this.employeeRepository = employeeRepository;
        this.orderPricingService = orderPricingService;
        this.reservationService = reservationService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional(readOnly = true)
    public ReservationPreorderResponse findForCustomer(String code, String phone) {
        TableReservation reservation = findByCode(code);
        verifyCustomerPhone(reservation, phone);
        return toResponse(reservation);
    }

    /**
     * Khách chỉ được chọn món trước sau khi lịch đặt bàn đã được nhà hàng xác nhận.
     * Nếu khách sửa thực đơn đã duyệt, trạng thái quay lại CHO_XAC_NHAN.
     */
    @Transactional
    public ReservationPreorderResponse saveByCustomer(String code,
                                                       String phone,
                                                       ReservationPreorderRequest request) {
        TableReservation reservation = findByCodeForUpdate(code);
        verifyCustomerPhone(reservation, phone);
        ensureCustomerCanEdit(reservation);

        if (PREORDER_SENT.equals(normalizeStatus(reservation.getTrangThaiDatMonTruoc()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Thực đơn đặt trước đã chuyển xuống bếp, không thể thay đổi"
            );
        }
        if (reservation.getDonHang() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lịch đặt bàn đã phát sinh đơn hàng, vui lòng gọi thêm món tại bàn"
            );
        }

        reservation.getChiTietDatMonTruoc().clear();
        for (ReservationPreorderRequest.Item requestedItem : request.items()) {
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

            ReservationPreorderItem item = new ReservationPreorderItem();
            item.setMonAn(food);
            item.setSoLuong(requestedItem.soLuong());
            item.setDonGia(food.getGia());
            item.setGhiChu(trimToNull(requestedItem.ghiChu()));
            reservation.addPreorderItem(item);
        }

        reservation.setTrangThaiDatMonTruoc(PREORDER_PENDING);
        reservation.setGhiChuDatMonTruoc(trimToNull(request.ghiChu()));
        reservation.setLyDoTuChoiDatMonTruoc(null);
        reservation.setThoiGianDatMonTruoc(LocalDateTime.now());
        reservation.setThoiGianXacNhanMonTruoc(null);
        reservation.setThoiGianDuKienChuyenBep(null);
        reservation.setThoiGianChuyenBep(null);
        reservation.setNguoiXacNhanMonTruoc(null);

        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_PREORDER_SUBMITTED",
                "Khách đã gửi thực đơn đặt trước cho lịch " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_PREORDER_SUBMITTED",
                "Khách đã gửi thực đơn đặt trước",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationPreorderResponse cancelByCustomer(String code,
                                                         String phone,
                                                         ReservationCancelRequest request) {
        TableReservation reservation = findByCodeForUpdate(code);
        verifyCustomerPhone(reservation, phone);
        String preorderStatus = normalizeStatus(reservation.getTrangThaiDatMonTruoc());
        if (Set.of(PREORDER_NONE, PREORDER_CANCELLED).contains(preorderStatus)
                || reservation.getChiTietDatMonTruoc().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch đặt bàn chưa có món đặt trước");
        }
        if (PREORDER_SENT.equals(preorderStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Món đã chuyển xuống bếp, vui lòng liên hệ nhân viên để xử lý"
            );
        }

        reservation.setTrangThaiDatMonTruoc(PREORDER_CANCELLED);
        reservation.setLyDoTuChoiDatMonTruoc(normalizeRequired(
                request.reason(),
                "Lý do hủy món đặt trước không được để trống"
        ));
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_PREORDER_CANCELLED_BY_CUSTOMER",
                "Khách đã hủy thực đơn đặt trước của lịch " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_PREORDER_CANCELLED",
                "Khách đã hủy thực đơn đặt trước",
                saved
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReservationPreorderResponse findForStaff(Integer reservationId,
                                                     String username,
                                                     boolean admin) {
        reservationService.findDetail(reservationId, username, admin);
        return toResponse(findById(reservationId));
    }

    @Transactional
    public ReservationPreorderResponse confirmByStaff(Integer reservationId,
                                                       ReservationPreorderConfirmRequest request,
                                                       String username,
                                                       boolean admin) {
        reservationService.findDetail(reservationId, username, admin);
        TableReservation reservation = findByIdForUpdate(reservationId);
        requirePreorderStatus(reservation, PREORDER_PENDING, "Chỉ có thể duyệt thực đơn đang chờ xác nhận");
        ensureReservationStillActiveForPreorder(reservation);
        if (reservation.getChiTietDatMonTruoc().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thực đơn đặt trước không có món");
        }
        ensurePreorderFoodsAvailable(reservation);

        Employee employee = requireActiveEmployee(username);
        int preparationMinutes = request == null || request.soPhutChuanBiTruoc() == null
                ? DEFAULT_PREPARATION_MINUTES
                : request.soPhutChuanBiTruoc();
        LocalDateTime plannedKitchenTime = reservation.getNgayGioDen().minusMinutes(preparationMinutes);

        reservation.setTrangThaiDatMonTruoc(PREORDER_CONFIRMED);
        reservation.setThoiGianXacNhanMonTruoc(LocalDateTime.now());
        reservation.setThoiGianDuKienChuyenBep(plannedKitchenTime);
        reservation.setNguoiXacNhanMonTruoc(employee);
        reservation.setLyDoTuChoiDatMonTruoc(null);
        if (request != null && StringUtils.hasText(request.ghiChu())) {
            reservation.setGhiChuDatMonTruoc(mergeNotes(
                    reservation.getGhiChuDatMonTruoc(),
                    request.ghiChu()
            ));
        }

        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_PREORDER_CONFIRMED",
                "Đã duyệt thực đơn đặt trước của lịch " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_PREORDER_CONFIRMED",
                "Thực đơn đặt trước đã được xác nhận",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationPreorderResponse rejectByStaff(Integer reservationId,
                                                      ReservationCancelRequest request,
                                                      String username,
                                                      boolean admin) {
        reservationService.findDetail(reservationId, username, admin);
        TableReservation reservation = findByIdForUpdate(reservationId);
        requireActiveEmployee(username);
        requirePreorderStatus(reservation, PREORDER_PENDING, "Chỉ có thể từ chối thực đơn đang chờ xác nhận");

        reservation.setTrangThaiDatMonTruoc(PREORDER_REJECTED);
        reservation.setLyDoTuChoiDatMonTruoc(normalizeRequired(
                request.reason(),
                "Lý do từ chối không được để trống"
        ));
        reservation.setThoiGianXacNhanMonTruoc(null);
        reservation.setThoiGianDuKienChuyenBep(null);
        reservation.setNguoiXacNhanMonTruoc(null);

        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_PREORDER_REJECTED",
                "Đã từ chối thực đơn đặt trước của lịch " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_PREORDER_REJECTED",
                "Thực đơn đặt trước cần được khách điều chỉnh",
                saved
        );
        return toResponse(saved);
    }

    /**
     * Chỉ chuyển món xuống bếp sau khi khách đã check-in và được xếp bàn thực tế.
     * Quy tắc này tránh chế biến cho khách không đến nhưng vẫn giúp bỏ qua bước gọi món tại bàn.
     */
    @Transactional
    public ReservationPreorderResponse sendToKitchen(Integer reservationId,
                                                      String username,
                                                      boolean admin) {
        var reservationDetail = reservationService.findDetail(reservationId, username, admin);
        if (reservationDetail.maBanThucTe() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ chuyển món xuống bếp sau khi khách đã check-in và được xếp bàn"
            );
        }

        // Khóa bàn trước, cùng thứ tự với luồng tạo đơn thường, để không thể phát sinh hai đơn đồng thời.
        DiningTable lockedTable = diningTableRepository.findByIdForUpdate(reservationDetail.maBanThucTe())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bàn thực tế của lịch đặt bàn"
                ));
        TableReservation reservation = findByIdForUpdate(reservationId);
        Employee employee = requireActiveEmployee(username);
        requirePreorderStatus(reservation, PREORDER_CONFIRMED, "Thực đơn đặt trước chưa được xác nhận");

        if (!RESERVATION_SEATED.equals(normalizeStatus(reservation.getTrangThai()))
                || reservation.getBanThucTe() == null
                || !lockedTable.getMaBan().equals(reservation.getBanThucTe().getMaBan())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn thực tế vừa thay đổi, vui lòng tải lại lịch đặt bàn"
            );
        }
        if (reservation.getDonHang() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lịch đặt bàn đã được liên kết với đơn hàng #DH" + reservation.getDonHang().getMaDonHang()
            );
        }
        if (reservation.getChiTietDatMonTruoc().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thực đơn đặt trước không có món");
        }
        if (!orderRepository.findOpenOrders(
                lockedTable.getMaBan(),
                OPEN_ORDER_STATUSES,
                PageRequest.of(0, 1)
        ).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn thực tế đang có đơn hàng khác, vui lòng kiểm tra lại"
            );
        }

        Order order = new Order();
        order.setBanAn(lockedTable);
        order.setNhanVien(employee);
        order.setTrangThai("DA_XAC_NHAN");
        order.setGhiChu(buildOrderNote(reservation));
        order.setTamTinh(BigDecimal.ZERO);
        order.setTienGiam(BigDecimal.ZERO);
        order.setTongTien(BigDecimal.ZERO);

        LocalDateTime now = LocalDateTime.now();
        for (ReservationPreorderItem preorderItem : reservation.getChiTietDatMonTruoc()) {
            Food food = preorderItem.getMonAn();
            if (food == null || !Boolean.TRUE.equals(food.getTrangThai())) {
                String foodName = food == null ? "không xác định" : food.getTenMonAn();
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Không thể chuyển xuống bếp vì món đang ngừng bán: " + foodName
                );
            }
            // Tách từng suất đặt trước thành một chi tiết riêng để bếp xử lý độc lập.
            for (int unit = 0; unit < preorderItem.getSoLuong(); unit++) {
                OrderItem orderItem = new OrderItem();
                orderItem.setMonAn(food);
                orderItem.setSoLuong(1);
                orderItem.setDonGia(preorderItem.getDonGia());
                orderItem.setGhiChu(preorderItem.getGhiChu());
                orderItem.setTrangThaiMon("CHO_BEP");
                orderItem.setLanGoi(1);
                orderItem.setThoiGianThem(now);
                order.addItem(orderItem);
            }
        }

        orderPricingService.recalculate(order);
        Order savedOrder = orderRepository.saveAndFlush(order);
        reservation.setDonHang(savedOrder);
        reservation.setTrangThaiDatMonTruoc(PREORDER_SENT);
        reservation.setThoiGianChuyenBep(now);
        TableReservation savedReservation = reservationRepository.saveAndFlush(reservation);

        systemActivityService.record(
                "RESERVATION_PREORDER_SENT_TO_KITCHEN",
                "Thực đơn đặt trước của lịch " + savedReservation.getMaTraCuu()
                        + " đã chuyển xuống bếp thành đơn #DH" + savedOrder.getMaDonHang(),
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyNewOrder(savedOrder);
        realtimeNotificationService.notifyKitchenOrderConfirmed(savedOrder);
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_PREORDER_SENT_TO_KITCHEN",
                "Món đặt trước đã được chuyển xuống bếp",
                savedReservation
        );
        return toResponse(savedReservation);
    }

    private void ensurePreorderFoodsAvailable(TableReservation reservation) {
        for (ReservationPreorderItem item : reservation.getChiTietDatMonTruoc()) {
            Food food = item.getMonAn();
            if (food == null || !Boolean.TRUE.equals(food.getTrangThai())) {
                String foodName = food == null ? "không xác định" : food.getTenMonAn();
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Món đặt trước đang ngừng bán: " + foodName
                );
            }
        }
    }

    private void ensureCustomerCanEdit(TableReservation reservation) {
        String reservationStatus = normalizeStatus(reservation.getTrangThai());
        if (!RESERVATION_CONFIRMED.equals(reservationStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ được chọn món trước sau khi lịch đặt bàn đã được xác nhận"
            );
        }
    }

    private void ensureReservationStillActiveForPreorder(TableReservation reservation) {
        String status = normalizeStatus(reservation.getTrangThai());
        if (!Set.of(RESERVATION_CONFIRMED, RESERVATION_ARRIVED, RESERVATION_SEATED).contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lịch đặt bàn không còn ở trạng thái cho phép xử lý món đặt trước"
            );
        }
    }

    private TableReservation findByCode(String code) {
        String normalized = normalizeRequired(code, "Mã tra cứu không được để trống");
        return reservationRepository.findByMaTraCuuIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt bàn"));
    }

    private TableReservation findByCodeForUpdate(String code) {
        String normalized = normalizeRequired(code, "Mã tra cứu không được để trống");
        return reservationRepository.findByCodeForUpdate(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt bàn"));
    }

    private TableReservation findById(Integer id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đặt bàn không hợp lệ");
        }
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt bàn: " + id));
    }

    private TableReservation findByIdForUpdate(Integer id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đặt bàn không hợp lệ");
        }
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt bàn: " + id));
    }

    private Employee requireActiveEmployee(String username) {
        if (!StringUtils.hasText(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản nhân viên");
        }
        Employee employee = employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
        if (!"DANG_LAM_VIEC".equals(normalizeStatus(employee.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản nhân viên đang ngừng hoạt động");
        }
        return employee;
    }

    private void verifyCustomerPhone(TableReservation reservation, String phone) {
        if (!normalizePhone(reservation.getSoDienThoai()).equals(normalizePhone(phone))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt bàn");
        }
    }

    private void requirePreorderStatus(TableReservation reservation, String expected, String message) {
        if (!expected.equals(normalizeStatus(reservation.getTrangThaiDatMonTruoc()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private ReservationPreorderResponse toResponse(TableReservation reservation) {
        List<ReservationPreorderResponse.Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReservationPreorderItem item : reservation.getChiTietDatMonTruoc()) {
            BigDecimal unitPrice = item.getDonGia() == null ? BigDecimal.ZERO : item.getDonGia();
            int quantity = item.getSoLuong() == null ? 0 : item.getSoLuong();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            total = total.add(lineTotal);
            Food food = item.getMonAn();
            items.add(new ReservationPreorderResponse.Item(
                    item.getMaChiTietDatMonTruoc(),
                    food == null ? null : food.getMaMonAn(),
                    food == null ? null : food.getTenMonAn(),
                    food == null ? null : food.getHinhAnh(),
                    item.getSoLuong(),
                    money(unitPrice),
                    money(lineTotal),
                    item.getGhiChu()
            ));
        }
        Employee confirmer = reservation.getNguoiXacNhanMonTruoc();
        return new ReservationPreorderResponse(
                reservation.getMaDatBan(),
                reservation.getMaTraCuu(),
                reservation.getTrangThai(),
                reservation.getNgayGioDen(),
                defaultPreorderStatus(reservation.getTrangThaiDatMonTruoc()),
                reservation.getGhiChuDatMonTruoc(),
                reservation.getLyDoTuChoiDatMonTruoc(),
                reservation.getThoiGianDatMonTruoc(),
                reservation.getThoiGianXacNhanMonTruoc(),
                reservation.getThoiGianDuKienChuyenBep(),
                reservation.getThoiGianChuyenBep(),
                confirmer == null ? null : confirmer.getMaNhanVien(),
                confirmer == null ? null : confirmer.getHoTen(),
                reservation.getDonHang() == null ? null : reservation.getDonHang().getMaDonHang(),
                money(total),
                items
        );
    }

    private String buildOrderNote(TableReservation reservation) {
        String base = "Đặt món trước - lịch " + reservation.getMaTraCuu();
        return mergeNotes(base, reservation.getGhiChuDatMonTruoc());
    }

    private String mergeNotes(String oldNote, String newNote) {
        String oldValue = trimToNull(oldNote);
        String newValue = trimToNull(newNote);
        if (oldValue == null) return newValue;
        if (newValue == null) return oldValue;
        return oldValue + " | " + newValue;
    }

    private String defaultPreorderStatus(String value) {
        return StringUtils.hasText(value) ? normalizeStatus(value) : PREORDER_NONE;
    }

    private String normalizePhone(String value) {
        String phone = normalizeRequired(value, "Số điện thoại không được để trống")
                .replaceAll("[^0-9+]", "");
        if (phone.length() < 8 || phone.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        return phone;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
