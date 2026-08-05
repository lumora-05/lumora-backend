package com.example.restaurant.service;

import com.example.restaurant.dto.*;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.ReservationPreorderItem;
import com.example.restaurant.entity.TableReservation;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.OrderRepository;
import com.example.restaurant.repository.TableReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationService {
    private static final String PENDING = "CHO_XAC_NHAN";
    private static final String CONFIRMED = "DA_XAC_NHAN";
    private static final String ARRIVED = "KHACH_DA_DEN";
    private static final String SEATED = "DA_XEP_BAN";
    private static final String COMPLETED = "HOAN_THANH";
    private static final String CANCELLED = "DA_HUY";
    private static final String REJECTED = "TU_CHOI";
    private static final String NO_SHOW = "KHONG_DEN";

    private static final Set<String> TERMINAL_STATUSES = Set.of(COMPLETED, CANCELLED, REJECTED, NO_SHOW);
    private static final Set<String> OVERLAP_STATUSES = Set.of(CONFIRMED, ARRIVED, SEATED);
    private static final Set<String> OPEN_ORDER_STATUSES = Set.of(
            "CHO_XAC_NHAN", "DA_XAC_NHAN", "DANG_CHUAN_BI", "DANG_CHE_BIEN",
            "SAN_SANG", "SAN_SANG_PHUC_VU", "DA_HOAN_THANH", "DA_PHUC_VU",
            "CHO_THANH_TOAN", "SAN_SANG_THANH_TOAN"
    );
    private static final int DEFAULT_DURATION_MINUTES = 120;
    private static final int TABLE_PREPARATION_MINUTES = 30;
    private static final int NO_SHOW_GRACE_MINUTES = 15;
    private static final DateTimeFormatter RESERVATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TableReservationRepository reservationRepository;
    private final DiningTableRepository diningTableRepository;
    private final EmployeeRepository employeeRepository;
    private final OrderRepository orderRepository;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;

    public ReservationService(TableReservationRepository reservationRepository,
                              DiningTableRepository diningTableRepository,
                              EmployeeRepository employeeRepository,
                              OrderRepository orderRepository,
                              RealtimeNotificationService realtimeNotificationService,
                              SystemActivityService systemActivityService) {
        this.reservationRepository = reservationRepository;
        this.diningTableRepository = diningTableRepository;
        this.employeeRepository = employeeRepository;
        this.orderRepository = orderRepository;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional
    public ReservationResponse create(ReservationCreateRequest request) {
        TableReservation reservation = new TableReservation();
        reservation.setMaTraCuu(generateLookupCode());
        applyCustomerData(reservation, request);
        reservation.setTrangThai(PENDING);
        TableReservation saved = reservationRepository.saveAndFlush(reservation);

        systemActivityService.record(
                "RESERVATION_CREATED",
                "Khách " + saved.getHoTenKhach() + " đã gửi yêu cầu đặt bàn " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_CREATED",
                "Có yêu cầu đặt bàn mới",
                saved
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<String> publicAreas() {
        return diningTableRepository.findAllByOrderByMaBanAsc().stream()
                .filter(table -> !Set.of("BAO_TRI").contains(normalizeStatus(table.getTrangThai())))
                .map(this::normalizeArea)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse findForCustomer(String code, String phone) {
        TableReservation reservation = findByCode(code);
        verifyCustomerPhone(reservation, phone);
        return toResponse(reservation);
    }

    /**
     * Khách được sửa khi đang chờ hoặc đã xác nhận. Nếu lịch đã xác nhận thay đổi,
     * yêu cầu quay lại CHO_XAC_NHAN để nhà hàng kiểm tra bàn và khung giờ lần nữa.
     */
    @Transactional
    public ReservationResponse updateByCustomer(String code,
                                                String phone,
                                                ReservationCreateRequest request) {
        TableReservation reservation = findByCodeForUpdate(code);
        verifyCustomerPhone(reservation, phone);
        String oldStatus = normalizeStatus(reservation.getTrangThai());
        if (!Set.of(PENDING, CONFIRMED).contains(oldStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể thay đổi lịch đặt bàn ở trạng thái hiện tại"
            );
        }

        applyCustomerData(reservation, request);
        if (CONFIRMED.equals(oldStatus)) {
            reservation.setTrangThai(PENDING);
            reservation.setBanDuKien(null);
            reservation.setNguoiXacNhan(null);
            reservation.setThoiGianXacNhan(null);
            reservation.setLyDoHuyTuChoi(null);
            resetPreorderAfterReservationChange(reservation);
        }
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_UPDATED_BY_CUSTOMER",
                "Khách đã cập nhật lịch đặt bàn " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_UPDATED",
                "Lịch đặt bàn đã được khách cập nhật",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse cancelByCustomer(String code,
                                                String phone,
                                                ReservationCancelRequest request) {
        TableReservation reservation = findByCodeForUpdate(code);
        verifyCustomerPhone(reservation, phone);
        String status = normalizeStatus(reservation.getTrangThai());
        if (!Set.of(PENDING, CONFIRMED).contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể hủy lịch đặt bàn ở trạng thái hiện tại"
            );
        }
        reservation.setTrangThai(CANCELLED);
        reservation.setLyDoHuyTuChoi(normalizeRequired(request.reason(), "Lý do hủy không được để trống"));
        cancelPreorderIfNotSent(reservation, "Lịch đặt bàn đã bị khách hủy");
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_CANCELLED_BY_CUSTOMER",
                "Khách đã hủy lịch đặt bàn " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_CANCELLED",
                "Khách đã hủy lịch đặt bàn",
                saved
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> findAll(String status,
                                                     LocalDate from,
                                                     LocalDate to,
                                                     String keyword,
                                                     String area,
                                                     int page,
                                                     int size,
                                                     String username,
                                                     boolean admin) {
        validateDateRange(from, to);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<TableReservation> spec = Specification.where(null);

        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            String normalizedStatus = normalizeStatus(status);
            validateKnownStatus(normalizedStatus);
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.<String>get("trangThai")), normalizedStatus));
        }
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ngayGioDen"), start));
        }
        if (to != null) {
            LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("ngayGioDen"), endExclusive));
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("maTraCuu")), pattern),
                    cb.like(cb.lower(root.<String>get("hoTenKhach")), pattern),
                    cb.like(cb.lower(root.<String>get("soDienThoai")), pattern)
            ));
        }

        String effectiveArea = admin ? trimToNull(area) : requireWaiterArea(username);
        if (StringUtils.hasText(effectiveArea)) {
            String normalizedArea = effectiveArea.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> {
                var expectedTable = root.join("banDuKien", JoinType.LEFT);
                var actualTable = root.join("banThucTe", JoinType.LEFT);
                var desiredArea = root.<String>get("khuVucMongMuon");
                var expectedArea = expectedTable.<String>get("khuVuc");
                var actualArea = actualTable.<String>get("khuVuc");

                boolean commonArea = "khu vực chung".equals(normalizedArea);
                var desiredAreaMatches = commonArea
                        ? cb.or(cb.isNull(desiredArea), cb.equal(desiredArea, ""),
                                cb.equal(cb.lower(desiredArea), normalizedArea))
                        : cb.equal(cb.lower(desiredArea), normalizedArea);
                var expectedAreaMatches = commonArea
                        ? cb.or(cb.isNull(expectedArea), cb.equal(expectedArea, ""),
                                cb.equal(cb.lower(expectedArea), normalizedArea))
                        : cb.equal(cb.lower(expectedArea), normalizedArea);
                var actualAreaMatches = commonArea
                        ? cb.or(cb.isNull(actualArea), cb.equal(actualArea, ""),
                                cb.equal(cb.lower(actualArea), normalizedArea))
                        : cb.equal(cb.lower(actualArea), normalizedArea);

                // Khu vực của bàn thực tế được ưu tiên, sau đó bàn dự kiến, rồi khu vực khách chọn.
                var areaMatches = cb.or(
                        cb.and(cb.isNotNull(root.get("banThucTe")), actualAreaMatches),
                        cb.and(cb.isNull(root.get("banThucTe")),
                                cb.isNotNull(root.get("banDuKien")), expectedAreaMatches),
                        cb.and(cb.isNull(root.get("banThucTe")),
                                cb.isNull(root.get("banDuKien")),
                                cb.isNotNull(desiredArea), desiredAreaMatches),
                        cb.and(cb.isNull(root.get("banThucTe")),
                                cb.isNull(root.get("banDuKien")),
                                cb.isNull(desiredArea),
                                commonArea ? cb.conjunction() : cb.disjunction())
                );
                if (admin) {
                    return areaMatches;
                }
                // Yêu cầu chưa chọn khu vực/bàn được hiển thị cho mọi phục vụ;
                // khi xác nhận, phục vụ chỉ được chọn bàn thuộc khu vực của mình.
                var unassigned = cb.and(
                        cb.isNull(root.get("khuVucMongMuon")),
                        cb.isNull(root.get("banDuKien")),
                        cb.isNull(root.get("banThucTe"))
                );
                return cb.or(areaMatches, unassigned);
            });
        }

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.asc("ngayGioDen"), Sort.Order.desc("maDatBan"))
        );
        Page<ReservationResponse> result = reservationRepository.findAll(spec, pageable).map(this::toResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ReservationResponse findDetail(Integer id, String username, boolean admin) {
        TableReservation reservation = findById(id);
        if (!admin) {
            ensureWaiterCanAccessReservation(reservation, username);
        }
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationAvailabilityResponse> availableTables(LocalDateTime arrival,
                                                                  Integer partySize,
                                                                  String area,
                                                                  Integer durationMinutes,
                                                                  Integer excludeReservationId,
                                                                  String username,
                                                                  boolean admin) {
        if (arrival == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày giờ đến không được để trống");
        }
        int safePartySize = partySize == null ? 1 : partySize;
        if (safePartySize < 1 || safePartySize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng khách không hợp lệ");
        }
        int safeDuration = normalizeDuration(durationMinutes);
        LocalDateTime end = arrival.plusMinutes(safeDuration);
        String effectiveArea = admin ? trimToNull(area) : requireWaiterArea(username);
        int excludedId = excludeReservationId == null ? 0 : excludeReservationId;

        return diningTableRepository.findAllByOrderByMaBanAsc().stream()
                .filter(table -> table.getSucChua() != null && table.getSucChua() >= safePartySize)
                .filter(table -> !StringUtils.hasText(effectiveArea)
                        || effectiveArea.trim().equalsIgnoreCase(normalizeArea(table)))
                .filter(table -> !Set.of("BAO_TRI", "DANG_DON_DEP").contains(normalizeStatus(table.getTrangThai())))
                .map(table -> new ReservationAvailabilityResponse(
                        table.getMaBan(),
                        table.getTenBan(),
                        table.getKhuVuc(),
                        table.getSucChua(),
                        table.getTrangThai(),
                        !hasOverlap(table.getMaBan(), arrival, end, excludedId)
                ))
                .toList();
    }


    /**
     * Bảo vệ bàn đã được giữ cho lịch đặt sắp tới trước khi bắt đầu một lượt phục vụ mới.
     * Một lượt khách trực tiếp được ước tính dùng bàn 120 phút và nhà hàng cần thêm
     * 30 phút để dọn, chuẩn bị bàn trước giờ khách đặt đến.
     */
    @Transactional(readOnly = true)
    public void ensureTableAvailableForNewService(DiningTable table) {
        if (table == null || table.getMaBan() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không xác định được bàn ăn");
        }

        LocalDateTime serviceStart = LocalDateTime.now();
        LocalDateTime serviceEndWithPreparation = serviceStart.plusMinutes(
                DEFAULT_DURATION_MINUTES + TABLE_PREPARATION_MINUTES
        );
        List<TableReservation> conflicts = reservationRepository.findConflictingReservationsForNewService(
                table.getMaBan(),
                serviceStart,
                serviceEndWithPreparation,
                Set.of(CONFIRMED, ARRIVED)
        );
        if (conflicts.isEmpty()) {
            return;
        }

        TableReservation nextReservation = conflicts.get(0);
        String reservedAt = nextReservation.getNgayGioDen() == null
                ? "sắp tới"
                : nextReservation.getNgayGioDen().format(RESERVATION_TIME_FORMAT);
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                table.getTenBan() + " đã được giữ cho lịch đặt lúc " + reservedAt
                        + ". Lượt phục vụ mới dự kiến kéo dài " + DEFAULT_DURATION_MINUTES
                        + " phút và cần " + TABLE_PREPARATION_MINUTES
                        + " phút chuẩn bị bàn. Vui lòng chọn bàn khác."
        );
    }

    @Transactional
    public ReservationResponse confirm(Integer id,
                                       ReservationConfirmRequest request,
                                       String username,
                                       boolean admin) {
        TableReservation reservation = findByIdForUpdate(id);
        ensureActorCanAccessReservation(reservation, username, admin);
        requireStatus(reservation, PENDING, "Chỉ có thể xác nhận yêu cầu đang chờ");
        Employee employee = requireActiveEmployee(username);
        DiningTable table = lockTable(request.maBanDuKien());
        if (!admin) {
            ensureWaiterCanAccessTable(table, username);
        }
        validateTableForReservation(table, reservation);
        ensureNoOverlap(table, reservation);

        reservation.setBanDuKien(table);
        reservation.setTrangThai(CONFIRMED);
        reservation.setNguoiXacNhan(employee);
        reservation.setThoiGianXacNhan(LocalDateTime.now());
        reservation.setLyDoHuyTuChoi(null);
        if (StringUtils.hasText(request.ghiChu())) {
            reservation.setGhiChu(mergeNotes(reservation.getGhiChu(), request.ghiChu()));
        }
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_CONFIRMED",
                "Đã xác nhận lịch " + saved.getMaTraCuu() + " và giữ dự kiến " + table.getTenBan(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_CONFIRMED",
                "Lịch đặt bàn đã được xác nhận",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse reject(Integer id,
                                      ReservationCancelRequest request,
                                      String username,
                                      boolean admin) {
        TableReservation reservation = findByIdForUpdate(id);
        ensureActorCanAccessReservation(reservation, username, admin);
        requireStatus(reservation, PENDING, "Chỉ có thể từ chối yêu cầu đang chờ");
        reservation.setTrangThai(REJECTED);
        reservation.setLyDoHuyTuChoi(normalizeRequired(request.reason(), "Lý do từ chối không được để trống"));
        cancelPreorderIfNotSent(reservation, "Lịch đặt bàn đã bị từ chối");
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_REJECTED",
                "Đã từ chối lịch đặt bàn " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_REJECTED",
                "Lịch đặt bàn đã bị từ chối",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse checkIn(Integer id, String username, boolean admin) {
        TableReservation reservation = findByIdForUpdate(id);
        ensureActorCanAccessReservation(reservation, username, admin);
        requireStatus(reservation, CONFIRMED, "Chỉ có thể check-in lịch đã xác nhận");
        Employee employee = requireActiveEmployee(username);
        reservation.setTrangThai(ARRIVED);
        reservation.setNguoiCheckIn(employee);
        reservation.setThoiGianCheckIn(LocalDateTime.now());
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_CHECKED_IN",
                "Khách của lịch " + saved.getMaTraCuu() + " đã đến nhà hàng",
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_CHECKED_IN",
                "Khách đặt bàn đã đến",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse assignTable(Integer id,
                                           ReservationAssignTableRequest request,
                                           String username,
                                           boolean admin) {
        TableReservation reservation = findByIdForUpdate(id);
        ensureActorCanAccessReservation(reservation, username, admin);
        requireStatus(reservation, ARRIVED, "Vui lòng check-in trước khi xếp bàn");
        Employee employee = requireActiveEmployee(username);
        DiningTable table = lockTable(request.maBan());
        if (!admin) {
            ensureWaiterCanAccessTable(table, username);
        }
        validateActualTable(table, reservation);
        ensureNoOverlap(table, reservation);

        reservation.setBanThucTe(table);
        reservation.setTrangThai(SEATED);
        reservation.setNguoiXepBan(employee);
        reservation.setThoiGianXepBan(LocalDateTime.now());
        table.setTrangThai("DANG_SU_DUNG");
        diningTableRepository.save(table);
        TableReservation saved = reservationRepository.saveAndFlush(reservation);

        systemActivityService.record(
                "RESERVATION_TABLE_ASSIGNED",
                "Đã xếp " + table.getTenBan() + " cho lịch " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_TABLE_ASSIGNED",
                "Khách đã được xếp bàn",
                saved
        );
        realtimeNotificationService.notifyTableArrangementChanged(
                "RESERVATION_TABLE_ASSIGNED",
                table.getTenBan() + " đã được xếp cho khách đặt bàn",
                toResponse(saved),
                List.of(table.getMaBan())
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse markNoShow(Integer id, String username, boolean admin) {
        TableReservation reservation = findByIdForUpdate(id);
        ensureActorCanAccessReservation(reservation, username, admin);
        requireStatus(reservation, CONFIRMED, "Chỉ lịch đã xác nhận mới có thể đánh dấu không đến");
        LocalDateTime allowedAt = reservation.getNgayGioDen().plusMinutes(NO_SHOW_GRACE_MINUTES);
        if (LocalDateTime.now().isBefore(allowedAt)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể đánh dấu không đến sau " + NO_SHOW_GRACE_MINUTES + " phút kể từ giờ hẹn"
            );
        }
        reservation.setTrangThai(NO_SHOW);
        reservation.setLyDoHuyTuChoi("Khách không đến sau thời gian giữ bàn");
        cancelPreorderIfNotSent(reservation, "Khách không đến sau thời gian giữ bàn");
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_NO_SHOW",
                "Lịch đặt bàn " + saved.getMaTraCuu() + " được đánh dấu khách không đến",
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_NO_SHOW",
                "Khách đặt bàn không đến",
                saved
        );
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse cancelByStaff(Integer id,
                                             ReservationCancelRequest request,
                                             String username,
                                             boolean admin) {
        TableReservation reservation = findByIdForUpdate(id);
        ensureActorCanAccessReservation(reservation, username, admin);
        String status = normalizeStatus(reservation.getTrangThai());
        if (TERMINAL_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch đặt bàn đã kết thúc");
        }
        if (reservation.getDonHang() != null
                && !Set.of("DA_THANH_TOAN", "DA_HUY").contains(normalizeStatus(reservation.getDonHang().getTrangThai()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lịch đặt bàn đã phát sinh đơn đang phục vụ, không thể hủy trực tiếp"
            );
        }
        releaseAssignedTableIfUnused(reservation);
        reservation.setTrangThai(CANCELLED);
        reservation.setLyDoHuyTuChoi(normalizeRequired(request.reason(), "Lý do hủy không được để trống"));
        cancelPreorderIfNotSent(reservation, "Lịch đặt bàn đã bị nhân viên hủy");
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        systemActivityService.record(
                "RESERVATION_CANCELLED_BY_STAFF",
                "Nhân viên đã hủy lịch đặt bàn " + saved.getMaTraCuu(),
                saved.getMaDatBan()
        );
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_CANCELLED",
                "Lịch đặt bàn đã bị hủy",
                saved
        );
        return toResponse(saved);
    }

    /**
     * Không tạo một đơn thường mới khi lịch đã xếp bàn còn thực đơn đặt trước đang chờ xử lý.
     * Nhân viên cần duyệt/chuyển món đặt trước xuống bếp hoặc hủy phần đặt trước trước.
     */
    @Transactional
    public void ensureNoPendingPreorderForAssignedReservation(DiningTable table) {
        if (table == null || table.getMaBan() == null) {
            return;
        }
        List<TableReservation> candidates = reservationRepository.findAssignedWithoutOrderForUpdate(
                table.getMaBan(),
                SEATED
        );
        for (TableReservation reservation : candidates) {
            String preorderStatus = normalizeStatus(reservation.getTrangThaiDatMonTruoc());
            if (!reservation.getChiTietDatMonTruoc().isEmpty()
                    && Set.of("CHO_XAC_NHAN", "DA_XAC_NHAN").contains(preorderStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Lịch " + reservation.getMaTraCuu()
                                + " đang có món đặt trước. Vui lòng xử lý món đặt trước trước khi tạo đơn mới."
                );
            }
        }
    }

    /** Gắn đơn đầu tiên được tạo tại bàn thực tế với lịch đã xếp bàn. */
    @Transactional
    public void linkOrderToAssignedReservation(Order order) {
        if (order == null || order.getMaDonHang() == null || order.getBanAn() == null
                || order.getBanAn().getMaBan() == null) {
            return;
        }
        List<TableReservation> candidates = reservationRepository.findAssignedWithoutOrderForUpdate(
                order.getBanAn().getMaBan(),
                SEATED
        );
        if (candidates.isEmpty()) {
            return;
        }
        TableReservation reservation = candidates.get(0);
        reservation.setDonHang(order);
        TableReservation saved = reservationRepository.saveAndFlush(reservation);
        realtimeNotificationService.notifyReservationChanged(
                "RESERVATION_ORDER_LINKED",
                "Lịch đặt bàn đã được liên kết với đơn hàng",
                saved
        );
    }

    /** Đồng bộ bàn thực tế khi đơn liên kết được chuyển sang bàn khác. */
    @Transactional
    public void transferByOrder(Order order, DiningTable targetTable) {
        if (order == null || order.getMaDonHang() == null || targetTable == null
                || targetTable.getMaBan() == null) {
            return;
        }
        reservationRepository.findByOrderIdForUpdate(order.getMaDonHang()).ifPresent(reservation -> {
            if (SEATED.equals(normalizeStatus(reservation.getTrangThai()))) {
                reservation.setBanThucTe(targetTable);
                TableReservation saved = reservationRepository.saveAndFlush(reservation);
                realtimeNotificationService.notifyReservationChanged(
                        "RESERVATION_TABLE_TRANSFERRED",
                        "Bàn của lịch đặt đã được chuyển",
                        saved
                );
            }
        });
    }

    /** Đồng bộ lịch đặt bàn khi đơn liên kết kết thúc bằng hủy đơn. */
    @Transactional
    public void cancelByOrder(Order order) {
        if (order == null || order.getMaDonHang() == null) {
            return;
        }
        reservationRepository.findByOrderIdForUpdate(order.getMaDonHang()).ifPresent(reservation -> {
            if (!TERMINAL_STATUSES.contains(normalizeStatus(reservation.getTrangThai()))) {
                reservation.setTrangThai(CANCELLED);
                reservation.setLyDoHuyTuChoi(mergeNotes(
                        reservation.getLyDoHuyTuChoi(),
                        "Đơn hàng liên kết đã bị hủy"
                ));
                reservation.setThoiGianHoanThanh(LocalDateTime.now());
                TableReservation saved = reservationRepository.saveAndFlush(reservation);
                systemActivityService.record(
                        "RESERVATION_CANCELLED_BY_ORDER",
                        "Lịch đặt bàn " + saved.getMaTraCuu() + " đã hủy do đơn hàng liên kết bị hủy",
                        saved.getMaDatBan()
                );
                realtimeNotificationService.notifyReservationChanged(
                        "RESERVATION_CANCELLED",
                        "Lịch đặt bàn đã hủy do đơn hàng liên kết bị hủy",
                        saved
                );
            }
        });
    }

    /** PaymentService gọi sau khi hóa đơn đã thanh toán thành công. */
    @Transactional
    public void completeByOrder(Order order) {
        if (order == null || order.getMaDonHang() == null) {
            return;
        }
        reservationRepository.findByOrderIdForUpdate(order.getMaDonHang()).ifPresent(reservation -> {
            if (!TERMINAL_STATUSES.contains(normalizeStatus(reservation.getTrangThai()))) {
                reservation.setTrangThai(COMPLETED);
                reservation.setThoiGianHoanThanh(LocalDateTime.now());
                TableReservation saved = reservationRepository.saveAndFlush(reservation);
                systemActivityService.record(
                        "RESERVATION_COMPLETED",
                        "Lịch đặt bàn " + saved.getMaTraCuu() + " đã hoàn thành sau thanh toán",
                        saved.getMaDatBan()
                );
                realtimeNotificationService.notifyReservationChanged(
                        "RESERVATION_COMPLETED",
                        "Lịch đặt bàn đã hoàn thành",
                        saved
                );
            }
        });
    }

    private void applyCustomerData(TableReservation reservation, ReservationCreateRequest request) {
        reservation.setHoTenKhach(normalizeRequired(request.hoTenKhach(), "Họ tên khách không được để trống"));
        reservation.setSoDienThoai(normalizePhone(request.soDienThoai()));
        LocalDateTime arrival = request.ngayGioDen();
        if (arrival == null || !arrival.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày giờ đến phải ở tương lai");
        }
        int duration = normalizeDuration(request.thoiLuongPhut());
        reservation.setNgayGioDen(arrival);
        reservation.setThoiLuongPhut(duration);
        reservation.setThoiGianKetThucDuKien(arrival.plusMinutes(duration));
        reservation.setSoLuongKhach(request.soLuongKhach());
        reservation.setKhuVucMongMuon(resolvePreferredArea(request.khuVucMongMuon()));
        reservation.setGhiChu(trimToNull(request.ghiChu()));
    }

    private void validateTableForReservation(DiningTable table, TableReservation reservation) {
        if (table.getSucChua() == null || table.getSucChua() < reservation.getSoLuongKhach()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    table.getTenBan() + " không đủ sức chứa cho " + reservation.getSoLuongKhach() + " khách"
            );
        }
        if (Set.of("BAO_TRI", "DANG_DON_DEP").contains(normalizeStatus(table.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, table.getTenBan() + " hiện không sẵn sàng");
        }
    }

    private void validateActualTable(DiningTable table, TableReservation reservation) {
        validateTableForReservation(table, reservation);
        if (!"TRONG".equals(normalizeStatus(table.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, table.getTenBan() + " không ở trạng thái trống");
        }
        if (StringUtils.hasText(table.getMaNhomBan()) || table.getMaBanChinh() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, table.getTenBan() + " đang thuộc nhóm ghép bàn");
        }
        if (!orderRepository.findOpenOrders(
                table.getMaBan(), OPEN_ORDER_STATUSES, PageRequest.of(0, 1)
        ).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, table.getTenBan() + " đang có đơn phục vụ");
        }
    }

    private void ensureNoOverlap(DiningTable table, TableReservation reservation) {
        if (hasOverlap(
                table.getMaBan(),
                reservation.getNgayGioDen(),
                reservation.getThoiGianKetThucDuKien(),
                reservation.getMaDatBan() == null ? 0 : reservation.getMaDatBan()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    table.getTenBan() + " đã có lịch đặt trùng khung giờ"
            );
        }
    }

    private boolean hasOverlap(Integer tableId,
                               LocalDateTime start,
                               LocalDateTime end,
                               Integer excludeId) {
        return reservationRepository.countOverlappingForTable(
                tableId,
                start,
                end,
                OVERLAP_STATUSES,
                excludeId == null ? 0 : excludeId
        ) > 0;
    }

    private void releaseAssignedTableIfUnused(TableReservation reservation) {
        DiningTable table = reservation.getBanThucTe();
        if (table == null || table.getMaBan() == null || reservation.getDonHang() != null) {
            return;
        }
        DiningTable locked = lockTable(table.getMaBan());
        if (orderRepository.findOpenOrders(
                locked.getMaBan(), OPEN_ORDER_STATUSES, PageRequest.of(0, 1)
        ).isEmpty()) {
            locked.setTrangThai("TRONG");
            diningTableRepository.save(locked);
            realtimeNotificationService.notifyTableArrangementChanged(
                    "RESERVATION_TABLE_RELEASED",
                    locked.getTenBan() + " đã được giải phóng",
                    toResponse(reservation),
                    List.of(locked.getMaBan())
            );
        }
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

    private DiningTable lockTable(Integer id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã bàn không được để trống");
        }
        return diningTableRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn ăn: " + id));
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

    private String requireWaiterArea(String username) {
        Employee employee = requireActiveEmployee(username);
        String role = employee.getVaiTro() == null ? "" : normalizeStatus(employee.getVaiTro().getTenVaiTro());
        if (!"WAITER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không phải nhân viên phục vụ");
        }
        if (!StringUtils.hasText(employee.getKhuVucPhuTrach())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên chưa được phân công khu vực");
        }
        return employee.getKhuVucPhuTrach().trim();
    }

    private void ensureActorCanAccessReservation(TableReservation reservation, String username, boolean admin) {
        if (!admin) {
            ensureWaiterCanAccessReservation(reservation, username);
        }
    }

    private void ensureWaiterCanAccessReservation(TableReservation reservation, String username) {
        String waiterArea = requireWaiterArea(username);
        if (reservation.getBanThucTe() == null
                && reservation.getBanDuKien() == null
                && !StringUtils.hasText(reservation.getKhuVucMongMuon())) {
            return;
        }
        String reservationArea = resolveReservationArea(reservation);
        if (!waiterArea.equalsIgnoreCase(reservationArea)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lịch đặt bàn không thuộc khu vực được phân công");
        }
    }

    private void ensureWaiterCanAccessTable(DiningTable table, String username) {
        String waiterArea = requireWaiterArea(username);
        if (!waiterArea.equalsIgnoreCase(normalizeArea(table))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bàn không thuộc khu vực được phân công");
        }
    }

    private String resolveReservationArea(TableReservation reservation) {
        if (reservation.getBanThucTe() != null && StringUtils.hasText(reservation.getBanThucTe().getKhuVuc())) {
            return reservation.getBanThucTe().getKhuVuc().trim();
        }
        if (reservation.getBanDuKien() != null && StringUtils.hasText(reservation.getBanDuKien().getKhuVuc())) {
            return reservation.getBanDuKien().getKhuVuc().trim();
        }
        if (StringUtils.hasText(reservation.getKhuVucMongMuon())) {
            return reservation.getKhuVucMongMuon().trim();
        }
        return "Khu vực chung";
    }

    private void verifyCustomerPhone(TableReservation reservation, String phone) {
        if (!normalizePhone(reservation.getSoDienThoai()).equals(normalizePhone(phone))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đặt bàn");
        }
    }

    private void requireStatus(TableReservation reservation, String expected, String message) {
        if (!expected.equals(normalizeStatus(reservation.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void validateKnownStatus(String status) {
        if (!Set.of(PENDING, CONFIRMED, ARRIVED, SEATED, COMPLETED, CANCELLED, REJECTED, NO_SHOW).contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đặt bàn không hợp lệ: " + status);
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }
    }

    private int normalizeDuration(Integer value) {
        int duration = value == null ? DEFAULT_DURATION_MINUTES : value;
        if (duration < 30 || duration > 360) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời lượng đặt bàn phải từ 30 đến 360 phút");
        }
        return duration;
    }

    private String generateLookupCode() {
        String code;
        do {
            code = "DB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        } while (reservationRepository.existsByMaTraCuuIgnoreCase(code));
        return code;
    }

    private ReservationResponse toResponse(TableReservation reservation) {
        DiningTable expected = reservation.getBanDuKien();
        DiningTable actual = reservation.getBanThucTe();
        Employee confirmer = reservation.getNguoiXacNhan();
        Employee checker = reservation.getNguoiCheckIn();
        Employee assigner = reservation.getNguoiXepBan();
        return new ReservationResponse(
                reservation.getMaDatBan(),
                reservation.getMaTraCuu(),
                reservation.getHoTenKhach(),
                reservation.getSoDienThoai(),
                reservation.getNgayGioDen(),
                reservation.getThoiGianKetThucDuKien(),
                reservation.getThoiLuongPhut(),
                reservation.getSoLuongKhach(),
                reservation.getKhuVucMongMuon(),
                expected == null ? null : expected.getMaBan(),
                expected == null ? null : expected.getTenBan(),
                actual == null ? null : actual.getMaBan(),
                actual == null ? null : actual.getTenBan(),
                reservation.getDonHang() == null ? null : reservation.getDonHang().getMaDonHang(),
                reservation.getGhiChu(),
                reservation.getTrangThai(),
                reservation.getLyDoHuyTuChoi(),
                reservation.getThoiGianTao(),
                reservation.getThoiGianCapNhat(),
                reservation.getThoiGianXacNhan(),
                reservation.getThoiGianCheckIn(),
                reservation.getThoiGianXepBan(),
                reservation.getThoiGianHoanThanh(),
                confirmer == null ? null : confirmer.getMaNhanVien(),
                confirmer == null ? null : confirmer.getHoTen(),
                checker == null ? null : checker.getMaNhanVien(),
                checker == null ? null : checker.getHoTen(),
                assigner == null ? null : assigner.getMaNhanVien(),
                assigner == null ? null : assigner.getHoTen(),
                StringUtils.hasText(reservation.getTrangThaiDatMonTruoc())
                        ? normalizeStatus(reservation.getTrangThaiDatMonTruoc()) : "CHUA_DAT",
                preorderQuantity(reservation),
                preorderTotal(reservation),
                reservation.getThoiGianDatMonTruoc(),
                reservation.getThoiGianXacNhanMonTruoc(),
                reservation.getThoiGianDuKienChuyenBep(),
                reservation.getThoiGianChuyenBep()
        );
    }

    private void resetPreorderAfterReservationChange(TableReservation reservation) {
        String preorderStatus = normalizeStatus(reservation.getTrangThaiDatMonTruoc());
        if (reservation.getChiTietDatMonTruoc().isEmpty()
                || !Set.of("CHO_XAC_NHAN", "DA_XAC_NHAN", "TU_CHOI").contains(preorderStatus)) {
            return;
        }
        reservation.setTrangThaiDatMonTruoc("CHO_XAC_NHAN");
        reservation.setLyDoTuChoiDatMonTruoc(null);
        reservation.setThoiGianXacNhanMonTruoc(null);
        reservation.setThoiGianDuKienChuyenBep(null);
        reservation.setThoiGianChuyenBep(null);
        reservation.setNguoiXacNhanMonTruoc(null);
    }

    private void cancelPreorderIfNotSent(TableReservation reservation, String reason) {
        if (reservation.getChiTietDatMonTruoc().isEmpty()
                || "DA_CHUYEN_BEP".equals(normalizeStatus(reservation.getTrangThaiDatMonTruoc()))) {
            return;
        }
        reservation.setTrangThaiDatMonTruoc("DA_HUY");
        reservation.setLyDoTuChoiDatMonTruoc(reason);
        reservation.setThoiGianDuKienChuyenBep(null);
    }

    private int preorderQuantity(TableReservation reservation) {
        return reservation.getChiTietDatMonTruoc().stream()
                .map(ReservationPreorderItem::getSoLuong)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private BigDecimal preorderTotal(TableReservation reservation) {
        BigDecimal total = reservation.getChiTietDatMonTruoc().stream()
                .map(item -> {
                    BigDecimal unitPrice = item.getDonGia() == null ? BigDecimal.ZERO : item.getDonGia();
                    int quantity = item.getSoLuong() == null ? 0 : item.getSoLuong();
                    return unitPrice.multiply(BigDecimal.valueOf(quantity));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizePhone(String value) {
        String phone = normalizeRequired(value, "Số điện thoại không được để trống")
                .replaceAll("[^0-9+]", "");
        if (phone.length() < 8 || phone.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        return phone;
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolvePreferredArea(String requestedArea) {
        String normalized = trimToNull(requestedArea);
        if (normalized == null) {
            return null;
        }
        return diningTableRepository.findAllByOrderByMaBanAsc().stream()
                .map(this::normalizeArea)
                .filter(area -> area.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Khu vực mong muốn không tồn tại"
                ));
    }

    private String normalizeArea(DiningTable table) {
        return table == null || !StringUtils.hasText(table.getKhuVuc())
                ? "Khu vực chung"
                : table.getKhuVuc().trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String mergeNotes(String oldNote, String newNote) {
        String normalizedNew = trimToNull(newNote);
        if (normalizedNew == null) {
            return oldNote;
        }
        String normalizedOld = trimToNull(oldNote);
        return normalizedOld == null ? normalizedNew : normalizedOld + " | " + normalizedNew;
    }
}
