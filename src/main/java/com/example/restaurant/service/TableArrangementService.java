package com.example.restaurant.service;

import com.example.restaurant.dto.TableArrangementResponse;
import com.example.restaurant.dto.TableMergeRequest;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Order;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TableArrangementService {
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

    private static final Set<String> PAYMENT_PENDING_STATUSES = Set.of(
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN"
    );

    private final DiningTableRepository diningTableRepository;
    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemActivityService systemActivityService;
    private final RealtimeNotificationService realtimeNotificationService;
    private final ServiceRequestService serviceRequestService;
    private final ReservationService reservationService;

    public TableArrangementService(DiningTableRepository diningTableRepository,
                                   OrderRepository orderRepository,
                                   EmployeeRepository employeeRepository,
                                   SystemActivityService systemActivityService,
                                   RealtimeNotificationService realtimeNotificationService,
                                   ServiceRequestService serviceRequestService,
                                   ReservationService reservationService) {
        this.diningTableRepository = diningTableRepository;
        this.orderRepository = orderRepository;
        this.employeeRepository = employeeRepository;
        this.systemActivityService = systemActivityService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.serviceRequestService = serviceRequestService;
        this.reservationService = reservationService;
    }

    /**
     * Chuyển toàn bộ đơn đang mở từ bàn nguồn sang một bàn trống khác.
     * Bàn đang thuộc nhóm ghép phải được tách trước để tránh làm sai cấu trúc nhóm.
     */
    @Transactional
    public TableArrangementResponse transfer(Integer sourceTableId,
                                             Integer targetTableId,
                                             String username,
                                             boolean admin) {
        if (sourceTableId == null || targetTableId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã bàn nguồn và bàn đích không được để trống");
        }
        if (sourceTableId.equals(targetTableId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn nguồn và bàn đích phải khác nhau");
        }

        Map<Integer, DiningTable> lockedTables = lockTables(List.of(sourceTableId, targetTableId));
        DiningTable source = requireLockedTable(lockedTables, sourceTableId);
        DiningTable target = requireLockedTable(lockedTables, targetTableId);

        ensureNotGrouped(source, "Bàn nguồn đang thuộc một nhóm ghép. Vui lòng tách bàn trước khi chuyển.");
        ensureNotGrouped(target, "Bàn đích đang thuộc một nhóm ghép. Vui lòng chọn bàn khác hoặc tách nhóm trước.");
        ensureActorCanAccessTables(username, admin, List.of(source, target));

        List<Order> sourceOrders = openOrdersForUpdate(source.getMaBan());
        if (sourceOrders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn nguồn không có đơn hàng đang phục vụ");
        }
        if (sourceOrders.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn nguồn đang có nhiều đơn mở. Vui lòng xử lý dữ liệu trùng trước khi chuyển bàn"
            );
        }
        if (!openOrdersForUpdate(target.getMaBan()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bàn đích đang có đơn hàng đang phục vụ");
        }
        if (!"TRONG".equals(normalize(target.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể chuyển sang bàn đang trống");
        }

        Order order = sourceOrders.get(0);
        Integer oldTableId = source.getMaBan();
        String oldTableName = source.getTenBan();

        order.setBanAn(target);
        source.setTrangThai("TRONG");
        target.setTrangThai(resolveOccupiedTableStatus(order));

        diningTableRepository.saveAllAndFlush(List.of(source, target));
        Order savedOrder = orderRepository.saveAndFlush(order);
        serviceRequestService.transferOpenRequests(oldTableId, target);
        reservationService.transferByOrder(savedOrder, target);

        String message = "Đã chuyển đơn #DH" + savedOrder.getMaDonHang()
                + " từ " + oldTableName + " sang " + target.getTenBan();
        systemActivityService.record("TABLE_TRANSFERRED", message, savedOrder.getMaDonHang());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maDonHang", savedOrder.getMaDonHang());
        payload.put("maBanNguon", oldTableId);
        payload.put("tenBanNguon", oldTableName);
        payload.put("maBanDich", target.getMaBan());
        payload.put("tenBanDich", target.getTenBan());
        payload.put("trangThaiDon", savedOrder.getTrangThai());
        realtimeNotificationService.notifyTableArrangementChanged(
                "TABLE_TRANSFERRED",
                message,
                payload,
                List.of(oldTableId, target.getMaBan())
        );
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedOrder);

        return new TableArrangementResponse(
                "CHUYEN_BAN",
                null,
                target,
                List.of(source, target),
                savedOrder
        );
    }

    /**
     * Ghép nhiều bàn thành một nhóm dùng chung một đơn. Bàn chính có thể đang có
     * một đơn mở; các bàn ghép bắt buộc phải trống và chưa có đơn mở.
     */
    @Transactional
    public TableArrangementResponse merge(TableMergeRequest request,
                                          String username,
                                          boolean admin) {
        if (request == null || request.maBanChinh() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã bàn chính không được để trống");
        }
        if (request.maBanGhep() == null || request.maBanGhep().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phải chọn ít nhất một bàn để ghép");
        }

        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        ids.add(request.maBanChinh());
        for (Integer id : request.maBanGhep()) {
            if (id == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã bàn ghép không được để trống");
            }
            if (request.maBanChinh().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn chính không thể đồng thời là bàn ghép");
            }
            if (!ids.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách bàn ghép bị trùng");
            }
        }

        Map<Integer, DiningTable> lockedTables = lockTables(ids);
        DiningTable primary = requireLockedTable(lockedTables, request.maBanChinh());
        List<DiningTable> allTables = ids.stream().map(id -> requireLockedTable(lockedTables, id)).toList();

        allTables.forEach(table -> ensureNotGrouped(
                table,
                table.getTenBan() + " đang thuộc một nhóm ghép khác"
        ));
        ensureSameArea(allTables);
        ensureActorCanAccessTables(username, admin, allTables);

        List<Order> primaryOrders = openOrdersForUpdate(primary.getMaBan());
        if (primaryOrders.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn chính đang có nhiều đơn mở. Vui lòng xử lý dữ liệu trùng trước khi ghép bàn"
            );
        }
        Order activeOrder = primaryOrders.isEmpty() ? null : primaryOrders.get(0);
        if (activeOrder == null && !"TRONG".equals(normalize(primary.getTrangThai()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bàn chính phải đang trống hoặc đang có đúng một đơn phục vụ"
            );
        }

        for (DiningTable table : allTables) {
            if (table.getMaBan().equals(primary.getMaBan())) {
                continue;
            }
            if (!"TRONG".equals(normalize(table.getTrangThai()))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        table.getTenBan() + " không ở trạng thái trống"
                );
            }
            if (!openOrdersForUpdate(table.getMaBan()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        table.getTenBan() + " đang có đơn hàng đang phục vụ"
                );
            }
        }

        String groupId = UUID.randomUUID().toString();
        String groupStatus = activeOrder == null ? "TRONG" : resolveOccupiedTableStatus(activeOrder);
        for (DiningTable table : allTables) {
            table.setMaNhomBan(groupId);
            table.setMaBanChinh(primary.getMaBan());
            table.setTrangThai(groupStatus);
        }
        diningTableRepository.saveAllAndFlush(allTables);

        String joinedNames = allTables.stream()
                .map(DiningTable::getTenBan)
                .toList()
                .toString();
        String message = "Đã ghép các bàn " + joinedNames + " với bàn chính " + primary.getTenBan();
        systemActivityService.record(
                "TABLES_MERGED",
                message,
                activeOrder == null ? primary.getMaBan() : activeOrder.getMaDonHang()
        );

        Map<String, Object> payload = buildGroupPayload(groupId, primary, allTables, activeOrder);
        realtimeNotificationService.notifyTableArrangementChanged(
                "TABLES_MERGED",
                message,
                payload,
                allTables.stream().map(DiningTable::getMaBan).toList()
        );
        realtimeNotificationService.notifyDashboardRefresh(payload);

        return new TableArrangementResponse(
                "GHEP_BAN",
                groupId,
                primary,
                allTables,
                activeOrder
        );
    }

    /**
     * Tách một nhóm bàn khi nhóm không còn đơn đang mở. Khi thanh toán hoặc hủy
     * đơn cuối cùng, nhóm cũng được tự động giải phóng nên endpoint này chủ yếu
     * dùng cho nhóm được tạo trước khi khách gọi món.
     */
    @Transactional
    public TableArrangementResponse unmerge(String groupId,
                                            String username,
                                            boolean admin) {
        String normalizedGroupId = normalizeRequired(groupId, "Mã nhóm bàn không được để trống");
        List<DiningTable> groupTables = diningTableRepository.findByMaNhomBanForUpdate(normalizedGroupId);
        if (groupTables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm bàn: " + normalizedGroupId);
        }

        ensureActorCanAccessTables(username, admin, groupTables);
        for (DiningTable table : groupTables) {
            if (!openOrdersForUpdate(table.getMaBan()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Nhóm bàn đang có đơn phục vụ, không thể tách bàn"
                );
            }
        }

        DiningTable primary = groupTables.stream()
                .filter(table -> table.getMaBan().equals(table.getMaBanChinh()))
                .findFirst()
                .orElse(groupTables.get(0));

        clearGroup(groupTables, "TRONG");
        diningTableRepository.saveAllAndFlush(groupTables);

        String message = "Đã tách nhóm bàn có bàn chính " + primary.getTenBan();
        systemActivityService.record("TABLES_UNMERGED", message, primary.getMaBan());

        Map<String, Object> payload = buildGroupPayload(normalizedGroupId, primary, groupTables, null);
        realtimeNotificationService.notifyTableArrangementChanged(
                "TABLES_UNMERGED",
                message,
                payload,
                groupTables.stream().map(DiningTable::getMaBan).toList()
        );
        realtimeNotificationService.notifyDashboardRefresh(payload);

        return new TableArrangementResponse(
                "TACH_BAN",
                null,
                primary,
                groupTables,
                null
        );
    }

    /** Trả về bàn chính của nhóm và khóa bản ghi để tạo/gọi thêm món an toàn. */
    @Transactional
    public DiningTable resolvePrimaryTableForUpdate(DiningTable selectedTable) {
        if (selectedTable == null || selectedTable.getMaBan() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không xác định được bàn ăn");
        }
        Integer primaryId = selectedTable.getMaBanChinh();
        if (primaryId == null || primaryId.equals(selectedTable.getMaBan())) {
            return selectedTable;
        }
        return diningTableRepository.findByIdForUpdate(primaryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Nhóm bàn không còn bàn chính hợp lệ"
                ));
    }

    /** Dùng cho các API đọc đơn hiện tại bằng QR của bàn phụ. */
    @Transactional(readOnly = true)
    public Integer resolvePrimaryTableId(Integer selectedTableId) {
        DiningTable selected = diningTableRepository.findById(selectedTableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bàn ăn: " + selectedTableId
                ));
        return selected.getMaBanChinh() == null ? selected.getMaBan() : selected.getMaBanChinh();
    }

    /** Đồng bộ trạng thái cho toàn bộ nhóm bàn; bàn độc lập vẫn hoạt động như cũ. */
    @Transactional
    public void updateServiceStatus(DiningTable primaryTable, String status) {
        if (primaryTable == null || primaryTable.getMaBan() == null) {
            return;
        }
        String normalizedStatus = normalizeRequired(status, "Trạng thái bàn không hợp lệ").toUpperCase(Locale.ROOT);
        List<DiningTable> tables = findGroupOrSingle(primaryTable);
        tables.forEach(table -> table.setTrangThai(normalizedStatus));
        diningTableRepository.saveAll(tables);
    }

    /**
     * Giải phóng bàn sau khi đơn cuối cùng kết thúc. Nếu là nhóm ghép thì tự tách
     * nhóm và đưa toàn bộ bàn về trạng thái trống.
     */
    @Transactional
    public void releaseAfterTerminalOrder(DiningTable primaryTable) {
        if (primaryTable == null || primaryTable.getMaBan() == null) {
            return;
        }
        String previousGroupId = primaryTable.getMaNhomBan();
        List<DiningTable> tables = findGroupOrSingle(primaryTable);
        List<Integer> tableIds = tables.stream().map(DiningTable::getMaBan).toList();
        serviceRequestService.cancelOpenRequestsForTables(
                tableIds,
                "Phiên phục vụ tại bàn đã kết thúc"
        );
        clearGroup(tables, "TRONG");
        diningTableRepository.saveAll(tables);

        if (StringUtils.hasText(previousGroupId) && tables.size() > 1) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("maNhomBan", previousGroupId);
            payload.put("maBanChinh", primaryTable.getMaBan());
            payload.put("maCacBan", tableIds);
            realtimeNotificationService.notifyTableArrangementChanged(
                    "TABLES_UNMERGED",
                    "Nhóm bàn đã được tự động tách sau khi đơn kết thúc",
                    payload,
                    tableIds
            );
        }
    }

    private Map<Integer, DiningTable> lockTables(Collection<Integer> ids) {
        List<Integer> sortedIds = ids.stream().distinct().sorted().toList();
        List<DiningTable> locked = diningTableRepository.findAllByIdsForUpdate(sortedIds);
        Map<Integer, DiningTable> result = new LinkedHashMap<>();
        locked.forEach(table -> result.put(table.getMaBan(), table));
        if (result.size() != sortedIds.size()) {
            List<Integer> missing = sortedIds.stream().filter(id -> !result.containsKey(id)).toList();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn ăn: " + missing);
        }
        return result;
    }

    private DiningTable requireLockedTable(Map<Integer, DiningTable> tables, Integer id) {
        DiningTable table = tables.get(id);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bàn ăn: " + id);
        }
        return table;
    }

    private List<Order> openOrdersForUpdate(Integer tableId) {
        return orderRepository.findOpenOrdersForUpdate(
                tableId,
                OPEN_ORDER_STATUSES,
                PageRequest.of(0, 2)
        );
    }

    private void ensureNotGrouped(DiningTable table, String message) {
        if (StringUtils.hasText(table.getMaNhomBan()) || table.getMaBanChinh() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void ensureSameArea(List<DiningTable> tables) {
        String expectedArea = normalizeArea(tables.get(0));
        boolean differentArea = tables.stream()
                .map(this::normalizeArea)
                .anyMatch(area -> !area.equalsIgnoreCase(expectedArea));
        if (differentArea) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể ghép các bàn trong cùng một khu vực"
            );
        }
    }

    private void ensureActorCanAccessTables(String username,
                                            boolean admin,
                                            List<DiningTable> tables) {
        if (admin) {
            return;
        }
        Employee waiter = resolveActiveWaiter(username);
        String assignedArea = waiter.getKhuVucPhuTrach().trim();
        boolean inaccessible = tables.stream()
                .anyMatch(table -> !assignedArea.equalsIgnoreCase(normalizeArea(table)));
        if (inaccessible) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Có bàn không thuộc khu vực được phân công cho nhân viên phục vụ"
            );
        }
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
                : employee.getVaiTro().getTenVaiTro().trim().toUpperCase(Locale.ROOT).replace("ROLE_", "");
        if (!"WAITER".equals(roleName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không phải nhân viên phục vụ");
        }
        if (!"DANG_LAM_VIEC".equals(normalize(employee.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên hiện không còn làm việc");
        }
        if (!StringUtils.hasText(employee.getKhuVucPhuTrach())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên phục vụ chưa được phân công khu vực");
        }
        return employee;
    }

    private String resolveOccupiedTableStatus(Order order) {
        return PAYMENT_PENDING_STATUSES.contains(normalize(order.getTrangThai()))
                ? "DANG_THANH_TOAN"
                : "DANG_SU_DUNG";
    }

    private List<DiningTable> findGroupOrSingle(DiningTable table) {
        if (!StringUtils.hasText(table.getMaNhomBan())) {
            return new ArrayList<>(List.of(table));
        }
        List<DiningTable> group = diningTableRepository.findByMaNhomBanOrderByMaBanAsc(table.getMaNhomBan());
        return group.isEmpty() ? new ArrayList<>(List.of(table)) : new ArrayList<>(group);
    }

    private void clearGroup(List<DiningTable> tables, String status) {
        tables.forEach(table -> {
            table.setMaNhomBan(null);
            table.setMaBanChinh(null);
            table.setTrangThai(status);
        });
    }

    private Map<String, Object> buildGroupPayload(String groupId,
                                                  DiningTable primary,
                                                  List<DiningTable> tables,
                                                  Order activeOrder) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("maNhomBan", groupId);
        payload.put("maBanChinh", primary == null ? null : primary.getMaBan());
        payload.put("tenBanChinh", primary == null ? null : primary.getTenBan());
        payload.put("maCacBan", tables.stream().map(DiningTable::getMaBan).toList());
        payload.put("tenCacBan", tables.stream().map(DiningTable::getTenBan).toList());
        payload.put("maDonHang", activeOrder == null ? null : activeOrder.getMaDonHang());
        return payload;
    }

    private String normalizeArea(DiningTable table) {
        return table != null && StringUtils.hasText(table.getKhuVuc())
                ? table.getKhuVuc().trim()
                : "Khu vực chung";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
