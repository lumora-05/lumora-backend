package com.example.restaurant.service;

import com.example.restaurant.dto.ServiceRequestCancelRequest;
import com.example.restaurant.dto.ServiceRequestCreateRequest;
import com.example.restaurant.dto.ServiceRequestResponse;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.ServiceRequest;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.ServiceRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ServiceRequestService {
    private static final List<String> ACTIVE_STATUSES = List.of("MOI", "DA_TIEP_NHAN");
    private static final Set<String> ALL_STATUSES = Set.of("MOI", "DA_TIEP_NHAN", "HOAN_THANH", "DA_HUY");
    private static final int MAX_ACTIVE_REQUESTS_PER_TABLE = 3;
    private static final long OVERDUE_MINUTES = 5L;

    private static final Map<String, String> REQUEST_TYPES = createRequestTypes();
    private static final Set<String> HIGH_PRIORITY_TYPES = Set.of("GOI_NHAN_VIEN", "YEU_CAU_KHAC");

    private final ServiceRequestRepository serviceRequestRepository;
    private final DiningTableRepository diningTableRepository;
    private final EmployeeRepository employeeRepository;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;

    public ServiceRequestService(ServiceRequestRepository serviceRequestRepository,
                                 DiningTableRepository diningTableRepository,
                                 EmployeeRepository employeeRepository,
                                 RealtimeNotificationService realtimeNotificationService,
                                 SystemActivityService systemActivityService) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.diningTableRepository = diningTableRepository;
        this.employeeRepository = employeeRepository;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
    }

    /** Khách tạo yêu cầu từ QR đang hoạt động của đúng bàn. */
    @Transactional
    public ServiceRequestResponse createByCustomer(String qrToken, ServiceRequestCreateRequest request) {
        DiningTable table = lockCustomerTable(qrToken);
        String type = normalizeRequestType(request.loaiYeuCau());
        String content = normalizeContent(type, request.noiDung());

        if (serviceRequestRepository.existsByMaBanAndLoaiYeuCauAndTrangThaiIn(
                table.getMaBan(), type, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Yêu cầu này đã được gửi và đang chờ nhân viên xử lý"
            );
        }
        long activeCount = serviceRequestRepository.countByMaBanAndTrangThaiIn(
                table.getMaBan(), ACTIVE_STATUSES);
        if (activeCount >= MAX_ACTIVE_REQUESTS_PER_TABLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn đang có quá nhiều yêu cầu chưa hoàn thành. Vui lòng chờ nhân viên xử lý"
            );
        }

        ServiceRequest entity = new ServiceRequest();
        entity.setMaBan(table.getMaBan());
        entity.setTenBan(table.getTenBan());
        entity.setKhuVuc(normalizeArea(table.getKhuVuc()));
        entity.setLoaiYeuCau(type);
        entity.setNoiDung(content);
        entity.setTrangThai("MOI");
        entity.setMucDoUuTien(HIGH_PRIORITY_TYPES.contains(type) ? "CAO" : "BINH_THUONG");
        entity.setThoiGianTao(LocalDateTime.now());

        ServiceRequest saved = serviceRequestRepository.saveAndFlush(entity);
        systemActivityService.record(
                "SERVICE_REQUEST_CREATED",
                saved.getTenBan() + " gửi yêu cầu: " + requestTypeLabel(saved.getLoaiYeuCau()),
                saved.getMaYeuCau()
        );
        realtimeNotificationService.notifyServiceRequestCreated(saved);
        return toResponse(saved);
    }

    /** Khách chỉ xem các yêu cầu chưa kết thúc của đúng QR. */
    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> findActiveByCustomerQr(String qrToken) {
        DiningTable table = requireCustomerTable(qrToken);
        return serviceRequestRepository
                .findByMaBanAndTrangThaiInOrderByThoiGianTaoDesc(table.getMaBan(), ACTIVE_STATUSES)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Lịch sử gần nhất giúp khách vẫn thấy trạng thái hoàn thành hoặc đã hủy sau khi tải lại trang. */
    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> findRecentByCustomerQr(String qrToken) {
        DiningTable table = requireCustomerTable(qrToken);
        return serviceRequestRepository.findTop20ByMaBanOrderByThoiGianTaoDesc(table.getMaBan())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Khách được tự hủy khi yêu cầu chưa có nhân viên tiếp nhận. */
    @Transactional
    public ServiceRequestResponse cancelByCustomer(String qrToken,
                                                   Integer requestId,
                                                   ServiceRequestCancelRequest request) {
        DiningTable table = requireCustomerTable(qrToken);
        ServiceRequest entity = lockRequest(requestId);
        ensureRequestBelongsToTable(entity, table.getMaBan());
        if (!"MOI".equals(normalizeStatus(entity.getTrangThai()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Yêu cầu đã được nhân viên tiếp nhận nên khách không thể tự hủy"
            );
        }

        cancel(entity, "KHACH_HANG", null, "Khách hàng", resolveCancelReason(request, "Khách không còn cần hỗ trợ"));
        ServiceRequest saved = serviceRequestRepository.saveAndFlush(entity);
        systemActivityService.record(
                "SERVICE_REQUEST_CANCELLED",
                saved.getTenBan() + " đã hủy yêu cầu phục vụ",
                saved.getMaYeuCau()
        );
        realtimeNotificationService.notifyServiceRequestCancelled(saved);
        return toResponse(saved);
    }

    /** Admin xem toàn bộ; phục vụ chỉ xem yêu cầu thuộc khu vực được phân công. */
    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> findForActor(String status,
                                                     String username,
                                                     boolean admin) {
        String filter = normalizeFilter(status);
        List<ServiceRequest> requests;
        if (admin) {
            resolveActiveEmployee(username, "ADMIN");
            requests = findForAdmin(filter);
        } else {
            Employee waiter = resolveActiveEmployee(username, "WAITER");
            String area = requireAssignedArea(waiter);
            requests = findForWaiter(area, filter);
        }

        Comparator<ServiceRequest> activeOrdering = Comparator
                .comparing((ServiceRequest item) -> !"CAO".equals(normalizeStatus(item.getMucDoUuTien())))
                .thenComparing(ServiceRequest::getThoiGianTao)
                .thenComparing(ServiceRequest::getMaYeuCau);
        if ("ACTIVE".equals(filter)) {
            requests = requests.stream().sorted(activeOrdering).toList();
        }
        return requests.stream().map(this::toResponse).toList();
    }

    /** Khóa bản ghi giúp chỉ một phục vụ tiếp nhận thành công. */
    @Transactional
    public ServiceRequestResponse accept(Integer requestId, String username) {
        Employee waiter = resolveActiveEmployee(username, "WAITER");
        ServiceRequest entity = lockRequest(requestId);
        ensureWaiterCanAccess(waiter, entity);
        if (!"MOI".equals(normalizeStatus(entity.getTrangThai()))) {
            if ("DA_TIEP_NHAN".equals(normalizeStatus(entity.getTrangThai()))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Yêu cầu đã được " + safeName(entity.getTenNhanVienTiepNhan()) + " tiếp nhận"
                );
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu không còn ở trạng thái mới");
        }

        entity.setTrangThai("DA_TIEP_NHAN");
        entity.setMaNhanVienTiepNhan(waiter.getMaNhanVien());
        entity.setTenNhanVienTiepNhan(waiter.getHoTen());
        entity.setThoiGianTiepNhan(LocalDateTime.now());
        ServiceRequest saved = serviceRequestRepository.saveAndFlush(entity);

        systemActivityService.record(
                "SERVICE_REQUEST_ACCEPTED",
                waiter.getHoTen() + " tiếp nhận yêu cầu tại " + saved.getTenBan(),
                saved.getMaYeuCau()
        );
        realtimeNotificationService.notifyServiceRequestAccepted(saved);
        return toResponse(saved);
    }

    /** Chỉ nhân viên đã tiếp nhận mới được xác nhận hoàn thành. */
    @Transactional
    public ServiceRequestResponse complete(Integer requestId, String username) {
        Employee waiter = resolveActiveEmployee(username, "WAITER");
        ServiceRequest entity = lockRequest(requestId);
        ensureWaiterCanAccess(waiter, entity);
        if (!"DA_TIEP_NHAN".equals(normalizeStatus(entity.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu chưa được tiếp nhận hoặc đã kết thúc");
        }
        if (!waiter.getMaNhanVien().equals(entity.getMaNhanVienTiepNhan())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Yêu cầu đang được nhân viên khác xử lý"
            );
        }

        entity.setTrangThai("HOAN_THANH");
        entity.setThoiGianHoanThanh(LocalDateTime.now());
        ServiceRequest saved = serviceRequestRepository.saveAndFlush(entity);

        systemActivityService.record(
                "SERVICE_REQUEST_COMPLETED",
                waiter.getHoTen() + " hoàn thành yêu cầu tại " + saved.getTenBan(),
                saved.getMaYeuCau()
        );
        realtimeNotificationService.notifyServiceRequestCompleted(saved);
        return toResponse(saved);
    }

    /** Admin hủy yêu cầu mới hoặc đang được tiếp nhận và phải lưu lý do. */
    @Transactional
    public ServiceRequestResponse cancelByAdmin(Integer requestId,
                                                ServiceRequestCancelRequest request,
                                                String username) {
        Employee admin = resolveActiveEmployee(username, "ADMIN");
        ServiceRequest entity = lockRequest(requestId);
        if (!ACTIVE_STATUSES.contains(normalizeStatus(entity.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã kết thúc, không thể hủy");
        }

        cancel(
                entity,
                "ADMIN",
                admin.getMaNhanVien(),
                admin.getHoTen(),
                resolveCancelReason(request, "Admin hủy yêu cầu")
        );
        ServiceRequest saved = serviceRequestRepository.saveAndFlush(entity);
        systemActivityService.record(
                "SERVICE_REQUEST_CANCELLED",
                admin.getHoTen() + " hủy yêu cầu tại " + saved.getTenBan(),
                saved.getMaYeuCau()
        );
        realtimeNotificationService.notifyServiceRequestCancelled(saved);
        return toResponse(saved);
    }

    /** Chuyển các yêu cầu đang mở cùng khách sang bàn mới khi thực hiện chuyển bàn. */
    @Transactional
    public void transferOpenRequests(Integer sourceTableId, DiningTable targetTable) {
        if (sourceTableId == null || targetTable == null || targetTable.getMaBan() == null) {
            return;
        }
        if (serviceRequestRepository.countByMaBanAndTrangThaiIn(targetTable.getMaBan(), ACTIVE_STATUSES) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn đích đang có yêu cầu phục vụ chưa xử lý"
            );
        }
        List<ServiceRequest> requests = serviceRequestRepository.findOpenByTableForUpdate(
                sourceTableId,
                ACTIVE_STATUSES
        );
        if (requests.isEmpty()) {
            return;
        }
        requests.forEach(item -> {
            item.setMaBan(targetTable.getMaBan());
            item.setTenBan(targetTable.getTenBan());
            item.setKhuVuc(normalizeArea(targetTable.getKhuVuc()));
        });
        List<ServiceRequest> saved = serviceRequestRepository.saveAllAndFlush(requests);
        saved.forEach(realtimeNotificationService::notifyServiceRequestTransferred);
    }

    /** Tự đóng yêu cầu còn mở khi phiên phục vụ kết thúc và bàn được giải phóng. */
    @Transactional
    public void cancelOpenRequestsForTables(Collection<Integer> tableIds, String reason) {
        if (tableIds == null || tableIds.isEmpty()) {
            return;
        }
        List<ServiceRequest> requests = serviceRequestRepository.findOpenByTablesForUpdate(
                tableIds,
                ACTIVE_STATUSES
        );
        if (requests.isEmpty()) {
            return;
        }
        String normalizedReason = StringUtils.hasText(reason)
                ? reason.trim()
                : "Phiên phục vụ tại bàn đã kết thúc";
        requests.forEach(item -> cancel(item, "HE_THONG", null, "Hệ thống", normalizedReason));
        List<ServiceRequest> saved = serviceRequestRepository.saveAllAndFlush(requests);
        saved.forEach(realtimeNotificationService::notifyServiceRequestCancelled);
    }

    private List<ServiceRequest> findForAdmin(String filter) {
        if ("ALL".equals(filter)) {
            return serviceRequestRepository.findAllByOrderByThoiGianTaoDesc();
        }
        if ("ACTIVE".equals(filter)) {
            return serviceRequestRepository.findByTrangThaiInOrderByThoiGianTaoAsc(ACTIVE_STATUSES);
        }
        return serviceRequestRepository.findByTrangThaiOrderByThoiGianTaoDesc(filter);
    }

    private List<ServiceRequest> findForWaiter(String area, String filter) {
        if ("ALL".equals(filter)) {
            return serviceRequestRepository.findByKhuVucIgnoreCaseOrderByThoiGianTaoDesc(area);
        }
        if ("ACTIVE".equals(filter)) {
            return serviceRequestRepository.findByKhuVucIgnoreCaseAndTrangThaiInOrderByThoiGianTaoAsc(
                    area,
                    ACTIVE_STATUSES
            );
        }
        return serviceRequestRepository.findByKhuVucIgnoreCaseAndTrangThaiOrderByThoiGianTaoDesc(
                area,
                filter
        );
    }

    private DiningTable lockCustomerTable(String qrToken) {
        String token = normalizeQrToken(qrToken);
        DiningTable table = diningTableRepository.findByQrTokenForUpdate(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã QR không hợp lệ"));
        validateCustomerTable(table);
        return table;
    }

    private DiningTable requireCustomerTable(String qrToken) {
        String token = normalizeQrToken(qrToken);
        DiningTable table = diningTableRepository.findByQrToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã QR không hợp lệ"));
        validateCustomerTable(table);
        return table;
    }

    private void validateCustomerTable(DiningTable table) {
        if (!StringUtils.hasText(table.getQrToken())
                || !StringUtils.hasText(table.getAnhQr())
                || !"DANG_HOAT_DONG".equals(normalizeStatus(table.getTrangThaiQr()))) {
            throw new ResponseStatusException(HttpStatus.GONE, "Mã QR của bàn đang tạm ngưng hoặc không còn sử dụng");
        }
        String tableStatus = normalizeStatus(table.getTrangThai());
        if (Set.of("BAO_TRI", "DANG_DON_DEP").contains(tableStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn hiện không thể gửi yêu cầu phục vụ"
            );
        }
    }

    private Employee resolveActiveEmployee(String username, String expectedRole) {
        if (!StringUtils.hasText(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản nhân viên");
        }
        Employee employee = employeeRepository.findByTenDangNhap(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy nhân viên theo tài khoản đăng nhập"
                ));
        String role = employee.getVaiTro() == null
                ? ""
                : normalizeStatus(employee.getVaiTro().getTenVaiTro()).replace("ROLE_", "");
        if (!expectedRole.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không có quyền xử lý yêu cầu này");
        }
        if (!"DANG_LAM_VIEC".equals(normalizeStatus(employee.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên hiện không còn làm việc");
        }
        return employee;
    }

    private String requireAssignedArea(Employee waiter) {
        if (!StringUtils.hasText(waiter.getKhuVucPhuTrach())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên phục vụ chưa được phân công khu vực");
        }
        return waiter.getKhuVucPhuTrach().trim();
    }

    private void ensureWaiterCanAccess(Employee waiter, ServiceRequest request) {
        String assignedArea = requireAssignedArea(waiter);
        if (!assignedArea.equalsIgnoreCase(normalizeArea(request.getKhuVuc()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Yêu cầu không thuộc khu vực được phân công cho nhân viên phục vụ"
            );
        }
    }

    private ServiceRequest lockRequest(Integer requestId) {
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã yêu cầu không hợp lệ");
        }
        return serviceRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy yêu cầu phục vụ: " + requestId
                ));
    }

    private void ensureRequestBelongsToTable(ServiceRequest request, Integer tableId) {
        if (request.getMaBan() == null || !request.getMaBan().equals(tableId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yêu cầu không thuộc bàn của mã QR này");
        }
    }

    private void cancel(ServiceRequest entity,
                        String source,
                        Integer actorId,
                        String actorName,
                        String reason) {
        entity.setTrangThai("DA_HUY");
        entity.setNguonHuy(source);
        entity.setMaNguoiHuy(actorId);
        entity.setTenNguoiHuy(actorName);
        entity.setLyDoHuy(reason);
        entity.setThoiGianHuy(LocalDateTime.now());
    }

    private ServiceRequestResponse toResponse(ServiceRequest entity) {
        LocalDateTime end = entity.getThoiGianTiepNhan() != null
                ? entity.getThoiGianTiepNhan()
                : terminalTimeOrNow(entity);
        long waitingMinutes = Math.max(0, Duration.between(entity.getThoiGianTao(), end).toMinutes());
        boolean waitingForAcceptance = "MOI".equals(normalizeStatus(entity.getTrangThai()));
        boolean overdue = waitingForAcceptance
                && Duration.between(entity.getThoiGianTao(), LocalDateTime.now()).toMinutes() >= OVERDUE_MINUTES;

        return new ServiceRequestResponse(
                entity.getMaYeuCau(),
                entity.getMaBan(),
                entity.getTenBan(),
                entity.getKhuVuc(),
                entity.getLoaiYeuCau(),
                requestTypeLabel(entity.getLoaiYeuCau()),
                entity.getNoiDung(),
                entity.getTrangThai(),
                entity.getMucDoUuTien(),
                entity.getMaNhanVienTiepNhan(),
                entity.getTenNhanVienTiepNhan(),
                entity.getThoiGianTao(),
                entity.getThoiGianTiepNhan(),
                entity.getThoiGianHoanThanh(),
                entity.getThoiGianHuy(),
                entity.getMaNguoiHuy(),
                entity.getTenNguoiHuy(),
                entity.getNguonHuy(),
                entity.getLyDoHuy(),
                waitingMinutes,
                overdue
        );
    }

    private LocalDateTime terminalTimeOrNow(ServiceRequest entity) {
        if (entity.getThoiGianHoanThanh() != null) {
            return entity.getThoiGianHoanThanh();
        }
        if (entity.getThoiGianHuy() != null) {
            return entity.getThoiGianHuy();
        }
        return LocalDateTime.now();
    }

    private String normalizeRequestType(String value) {
        String type = normalizeStatus(value);
        if (!REQUEST_TYPES.containsKey(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loại yêu cầu không hợp lệ. Chỉ chấp nhận: " + String.join(", ", REQUEST_TYPES.keySet())
            );
        }
        return type;
    }

    private String normalizeContent(String type, String value) {
        String content = trimToNull(value);
        if ("YEU_CAU_KHAC".equals(type) && content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập nội dung cho yêu cầu khác");
        }
        return content;
    }

    private String resolveCancelReason(ServiceRequestCancelRequest request, String fallback) {
        String reason = request == null ? null : trimToNull(request.lyDo());
        return reason == null ? fallback : reason;
    }

    private String normalizeFilter(String value) {
        String filter = StringUtils.hasText(value) ? normalizeStatus(value) : "ACTIVE";
        if (!"ACTIVE".equals(filter) && !"ALL".equals(filter) && !ALL_STATUSES.contains(filter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc trạng thái yêu cầu không hợp lệ");
        }
        return filter;
    }

    private String requestTypeLabel(String type) {
        return REQUEST_TYPES.getOrDefault(normalizeStatus(type), "Yêu cầu phục vụ");
    }

    private String normalizeQrToken(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token không được để trống");
        }
        return value.trim();
    }

    private String normalizeArea(String value) {
        return StringUtils.hasText(value) ? value.trim() : "Khu vực chung";
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeName(String value) {
        return StringUtils.hasText(value) ? value.trim() : "nhân viên khác";
    }

    private static Map<String, String> createRequestTypes() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("GOI_NHAN_VIEN", "Gọi nhân viên");
        result.put("THEM_NUOC", "Xin thêm nước");
        result.put("THEM_DUNG_CU", "Xin thêm dụng cụ");
        result.put("THEM_KHAN_GIAY", "Xin thêm khăn giấy");
        result.put("DON_BAN", "Dọn bàn");
        result.put("YEU_CAU_KHAC", "Yêu cầu khác");
        return Map.copyOf(result);
    }
}
