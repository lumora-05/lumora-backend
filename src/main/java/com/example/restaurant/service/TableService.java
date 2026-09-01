package com.example.restaurant.service;

import com.example.restaurant.dto.QrStatusUpdateRequest;
import com.example.restaurant.dto.TableRequest;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TableService {
    private static final Set<String> TABLE_STATUSES = Set.of(
            "TRONG",
            "DANG_SU_DUNG",
            "DAT_TRUOC",
            "DANG_DON_DEP",
            "BAO_TRI",
            "DANG_THANH_TOAN"
    );

    /**
     * Trạng thái mà Admin được phép chuyển thủ công.
     * Các trạng thái phục vụ/thanh toán phải do luồng nghiệp vụ tự cập nhật để
     * tránh bàn và đơn hàng bị lệch trạng thái.
     */
    private static final Set<String> ADMIN_MANUAL_TABLE_STATUSES = Set.of(
            "TRONG",
            "BAO_TRI"
    );

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

    private static final Set<String> QR_STATUSES = Set.of(
            "DANG_HOAT_DONG",
            "TAM_NGUNG",
            "NGUNG_SU_DUNG"
    );

    private final DiningTableRepository diningTableRepository;
    private final EmployeeRepository employeeRepository;
    private final OrderRepository orderRepository;
    private final QrCodeService qrCodeService;

    public TableService(DiningTableRepository diningTableRepository,
                        EmployeeRepository employeeRepository,
                        OrderRepository orderRepository,
                        QrCodeService qrCodeService) {
        this.diningTableRepository = diningTableRepository;
        this.employeeRepository = employeeRepository;
        this.orderRepository = orderRepository;
        this.qrCodeService = qrCodeService;
    }

    @Transactional(readOnly = true)
    public List<DiningTable> findAll() {
        return diningTableRepository.findAllByOrderByMaBanAsc();
    }

    /** Danh sách bàn thuộc các khu vực được phân công cho nhân viên phục vụ. */
    @Transactional(readOnly = true)
    public List<DiningTable> findAllForWaiter(String username) {
        Employee waiter = resolveActiveWaiter(username);
        Set<String> assignedAreas = WaiterAreaAccess.assignedAreaKeys(waiter);
        if (assignedAreas.isEmpty()) {
            return List.of();
        }
        return diningTableRepository.findByKhuVucInIgnoreCaseOrderByMaBanAsc(assignedAreas);
    }

    @Transactional(readOnly = true)
    public DiningTable findById(Integer id) {
        return diningTableRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn ăn: " + id));
    }

    @Transactional(readOnly = true)
    public DiningTable findByIdForWaiter(Integer id, String username) {
        DiningTable table = findById(id);
        ensureWaiterCanAccessTable(resolveActiveWaiter(username), table);
        return table;
    }

    @Transactional(readOnly = true)
    public DiningTable findByQrToken(String qrToken) {
        String normalizedToken = normalizeQrToken(qrToken);
        return diningTableRepository.findByQrToken(normalizedToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mã QR không hợp lệ"));
    }

    /**
     * Chỉ cho khách mở menu khi bàn đã có QR đang hoạt động.
     */
    @Transactional(readOnly = true)
    public DiningTable findCustomerAccessibleTable(Integer id) {
        DiningTable table = findById(id);
        validateCustomerQrAccess(table);
        return table;
    }

    @Transactional(readOnly = true)
    public DiningTable findCustomerAccessibleTableByToken(String qrToken) {
        DiningTable table = findByQrToken(qrToken);
        validateCustomerQrAccess(table);
        return table;
    }

    @Transactional
    public DiningTable create(TableRequest request) {
        String tableName = normalizeRequired(request.tenBan(), "Tên bàn không được để trống");
        if (diningTableRepository.existsByTenBanIgnoreCase(tableName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên bàn đã tồn tại: " + tableName);
        }

        DiningTable table = new DiningTable();
        applyEditableFields(table, request);
        table.setTrangThai(resolveCreateStatus(request.trangThai()));
        table.setMaQr(null);
        table.setQrToken(generateUniqueQrToken());
        table.setAnhQr(null);
        table.setTrangThaiQr("CHUA_TAO");
        table.setNgayTaoQr(null);
        table.setNgayCapNhatQr(null);
        return diningTableRepository.save(table);
    }

    @Transactional
    public DiningTable update(Integer id, TableRequest request) {
        DiningTable table = findById(id);
        String tableName = normalizeRequired(request.tenBan(), "Tên bàn không được để trống");
        if (diningTableRepository.existsByTenBanIgnoreCaseAndMaBanNot(tableName, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên bàn đã tồn tại: " + tableName);
        }

        applyEditableFields(table, request);
        applyAdminStatusUpdate(table, request.trangThai());
        return diningTableRepository.save(table);
    }

    @Transactional
    public DiningTable generateQr(Integer id) {
        DiningTable table = findById(id);
        LocalDateTime now = LocalDateTime.now();

        table.setMaQr(formatQrCode(table.getMaBan()));
        if (!StringUtils.hasText(table.getQrToken())) {
            table.setQrToken(generateUniqueQrToken());
        }
        table.setAnhQr(qrCodeService.generateTableQr(table));
        table.setTrangThaiQr("DANG_HOAT_DONG");
        if (table.getNgayTaoQr() == null) {
            table.setNgayTaoQr(now);
        }
        table.setNgayCapNhatQr(now);
        return diningTableRepository.save(table);
    }

    @Transactional
    public DiningTable updateQrStatus(Integer id, QrStatusUpdateRequest request) {
        DiningTable table = findById(id);
        if (!StringUtils.hasText(table.getAnhQr())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn chưa có mã QR. Hãy tạo QR trước.");
        }

        String status = normalizeStatus(request.trangThaiQr());
        if (!QR_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái QR không hợp lệ. Chỉ chấp nhận DANG_HOAT_DONG, TAM_NGUNG hoặc NGUNG_SU_DUNG"
            );
        }

        table.setTrangThaiQr(status);
        table.setNgayCapNhatQr(LocalDateTime.now());
        return diningTableRepository.save(table);
    }

    @Transactional
    public void delete(Integer id) {
        DiningTable table = findById(id);
        if (StringUtils.hasText(table.getMaNhomBan()) || table.getMaBanChinh() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn đang thuộc một nhóm ghép. Vui lòng tách bàn trước khi xóa."
            );
        }
        qrCodeService.deleteTableQrImage(table);
        diningTableRepository.delete(table);
    }

    private void applyEditableFields(DiningTable table, TableRequest request) {
        table.setTenBan(normalizeRequired(request.tenBan(), "Tên bàn không được để trống"));
        table.setGhiChu(trimToNull(request.ghiChu()));
        table.setKhuVuc(resolveArea(request));
        table.setSucChua(request.sucChua() == null ? 4 : request.sucChua());
    }

    private String resolveCreateStatus(String requestedStatus) {
        String status = StringUtils.hasText(requestedStatus)
                ? normalizeStatus(requestedStatus)
                : "TRONG";
        validateKnownTableStatus(status);
        if (!ADMIN_MANUAL_TABLE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bàn mới chỉ được tạo ở trạng thái TRONG hoặc BAO_TRI"
            );
        }
        return status;
    }

    private void applyAdminStatusUpdate(DiningTable table, String requestedStatus) {
        // Không truyền trạng thái khi sửa tên/khu vực/sức chứa thì giữ nguyên trạng thái hiện tại.
        if (!StringUtils.hasText(requestedStatus)) {
            return;
        }

        String currentStatus = normalizeStatus(table.getTrangThai());
        String targetStatus = normalizeStatus(requestedStatus);
        validateKnownTableStatus(targetStatus);

        // Cho phép frontend gửi lại đúng trạng thái hiện tại khi chỉ sửa thông tin bàn.
        if (targetStatus.equals(currentStatus)) {
            return;
        }

        if (!ADMIN_MANUAL_TABLE_STATUSES.contains(currentStatus)
                || !ADMIN_MANUAL_TABLE_STATUSES.contains(targetStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể đổi thủ công trạng thái bàn từ " + currentStatus + " sang " + targetStatus
                            + ". Trạng thái phục vụ/thanh toán do hệ thống tự cập nhật."
            );
        }

        ensureTableHasNoOpenOrder(table);
        table.setTrangThai(targetStatus);
    }

    private void validateKnownTableStatus(String status) {
        if (!TABLE_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái bàn không hợp lệ: " + status);
        }
    }

    private void ensureTableHasNoOpenOrder(DiningTable table) {
        boolean hasOpenOrder = !orderRepository.findOpenOrders(
                table.getMaBan(),
                OPEN_ORDER_STATUSES,
                PageRequest.of(0, 1)
        ).isEmpty();
        if (hasOpenOrder) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn đang có đơn hàng hoạt động nên không thể thay đổi trạng thái thủ công"
            );
        }
    }

    private String resolveArea(TableRequest request) {
        if (StringUtils.hasText(request.khuVuc())) {
            return request.khuVuc().trim();
        }
        if (StringUtils.hasText(request.tenKhuVuc())) {
            return request.tenKhuVuc().trim();
        }
        return "Khu vực chung";
    }

    private String formatQrCode(Integer tableId) {
        return "QR" + String.format("%04d", tableId);
    }

    private String generateUniqueQrToken() {
        String token;
        do {
            token = UUID.randomUUID().toString();
        } while (diningTableRepository.existsByQrToken(token));
        return token;
    }

    private String normalizeQrToken(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR token không được để trống");
        }
        return value.trim();
    }

    private void validateCustomerQrAccess(DiningTable table) {
        if (!StringUtils.hasText(table.getQrToken())
                || !StringUtils.hasText(table.getAnhQr())
                || !"DANG_HOAT_DONG".equals(table.getTrangThaiQr())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Mã QR của bàn đang tạm ngưng hoặc không còn sử dụng");
        }
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase();
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

    private Employee resolveActiveWaiter(String username) {
        if (!StringUtils.hasText(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản nhân viên");
        }
        Employee employee = employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên theo tài khoản đăng nhập"
                ));
        String roleName = employee.getVaiTro() == null || employee.getVaiTro().getTenVaiTro() == null
                ? ""
                : employee.getVaiTro().getTenVaiTro().trim().toUpperCase().replace("ROLE_", "");
        if (!"WAITER".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không phải nhân viên phục vụ");
        }
        if (!"DANG_LAM_VIEC".equalsIgnoreCase(employee.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên hiện không còn làm việc");
        }
        return employee;
    }

    private void ensureWaiterCanAccessTable(Employee waiter, DiningTable table) {
        if (!WaiterAreaAccess.hasAssignedAreas(waiter)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Nhân viên phục vụ chưa được phân công khu vực"
            );
        }
        String tableArea = table == null ? null : table.getKhuVuc();
        if (!WaiterAreaAccess.canAccessArea(waiter, tableArea)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bàn không thuộc khu vực được phân công cho nhân viên phục vụ"
            );
        }
    }
}
