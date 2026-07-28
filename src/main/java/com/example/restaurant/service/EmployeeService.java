package com.example.restaurant.service;

import com.example.restaurant.dto.EmployeeRequest;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Role;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.InvoiceRepository;
import com.example.restaurant.repository.OrderRepository;
import com.example.restaurant.repository.PasswordResetCodeRepository;
import com.example.restaurant.repository.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemActivityService systemActivityService;
    private final RealtimeNotificationService realtimeNotificationService;

    public EmployeeService(EmployeeRepository employeeRepository,
            RoleRepository roleRepository,
            OrderRepository orderRepository,
            InvoiceRepository invoiceRepository,
            PasswordResetCodeRepository passwordResetCodeRepository,
            PasswordEncoder passwordEncoder,
            SystemActivityService systemActivityService,
            RealtimeNotificationService realtimeNotificationService) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemActivityService = systemActivityService;
        this.realtimeNotificationService = realtimeNotificationService;
    }

    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Employee> findPage(int page,
                                   int size,
                                   String keyword,
                                   String role,
                                   String status) {
        Specification<Employee> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("hoTen")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("soDienThoai")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("tenDangNhap")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("khuVucPhuTrach")), pattern)
            ));
        }
        if (role != null && !role.isBlank() && !"ALL".equalsIgnoreCase(role)) {
            String normalizedRole = role.trim().toUpperCase().replace("ROLE_", "");
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(criteriaBuilder.upper(root.get("vaiTro").get("tenVaiTro")), normalizedRole));
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            String normalizedStatus = status.trim().toUpperCase();
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(criteriaBuilder.upper(root.get("trangThai")), normalizedStatus));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("ngayTao"), Sort.Order.desc("maNhanVien"))
        );
        return employeeRepository.findAll(specification, pageable);
    }

    public Employee findById(Integer id) {
        return employeeRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên: " + id));
    }

    @Transactional
    public Employee create(EmployeeRequest request) {
        if (employeeRepository.existsByTenDangNhap(request.tenDangNhap())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập đã tồn tại");
        }
        if (request.matKhau() == null || request.matKhau().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu không được bỏ trống khi tạo nhân viên");
        }
        Employee employee = new Employee();
        apply(employee, request, true);
        Employee savedEmployee = employeeRepository.save(employee);
        systemActivityService.record(
                "EMPLOYEE_CREATED",
                "Nhân viên " + savedEmployee.getHoTen() + " đã được thêm vào hệ thống",
                savedEmployee.getMaNhanVien());
        realtimeNotificationService.notifyDashboardRefresh(savedEmployee);
        return savedEmployee;
    }

    @Transactional
    public Employee update(Integer id, EmployeeRequest request) {
        Employee employee = findById(id);
        apply(employee, request, false);
        Employee savedEmployee = employeeRepository.save(employee);
        systemActivityService.record(
                "EMPLOYEE_UPDATED",
                "Thông tin nhân viên " + savedEmployee.getHoTen() + " đã được cập nhật",
                savedEmployee.getMaNhanVien());
        realtimeNotificationService.notifyDashboardRefresh(savedEmployee);
        return savedEmployee;
    }

    @Transactional
    public void delete(Integer id) {
        Employee employee = findById(id);

        boolean usedInOrders = orderRepository.existsByNhanVien_MaNhanVien(id);
        boolean usedInInvoices = invoiceRepository.existsByNhanVien_MaNhanVien(id);
        if (usedInOrders || usedInInvoices) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nhân viên đã phát sinh đơn hàng hoặc hóa đơn nên không thể xóa vĩnh viễn. "
                            + "Hãy chuyển nhân viên sang trạng thái đã nghỉ để bảo toàn lịch sử giao dịch"
            );
        }

        String employeeName = employee.getHoTen();

        // Mã OTP chỉ là dữ liệu kỹ thuật của tài khoản, có thể dọn trước khi xóa nhân viên.
        passwordResetCodeRepository.deleteByEmployee_MaNhanVien(id);
        employeeRepository.delete(employee);
        employeeRepository.flush();

        systemActivityService.record(
                "EMPLOYEE_DELETED",
                "Nhân viên " + employeeName + " đã bị xóa khỏi hệ thống",
                id);
        realtimeNotificationService.notifyDashboardRefresh(Map.of(
                "maNhanVien", id,
                "hoTen", employeeName,
                "deleted", true
        ));
    }

    private void apply(Employee employee, EmployeeRequest request, boolean creating) {
        Role role = roleRepository.findByTenVaiTro(request.tenVaiTro())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy vai trò: " + request.tenVaiTro()));
        employee.setHoTen(request.hoTen());
        employee.setSoDienThoai(request.soDienThoai());
        employee.setEmail(request.email());
        employee.setTenDangNhap(request.tenDangNhap());
        if (creating || (request.matKhau() != null && !request.matKhau().isBlank())) {
            employee.setMatKhau(passwordEncoder.encode(request.matKhau()));
        }
        employee.setVaiTro(role);
        applyAssignedArea(employee, request, role, creating);
        if (request.trangThai() != null) {
            employee.setTrangThai(request.trangThai());
        }
    }

    private void applyAssignedArea(Employee employee,
                                   EmployeeRequest request,
                                   Role role,
                                   boolean creating) {
        String roleName = role.getTenVaiTro() == null
                ? ""
                : role.getTenVaiTro().trim().toUpperCase().replace("ROLE_", "");

        if (!"WAITER".equals(roleName)) {
            employee.setKhuVucPhuTrach(null);
            return;
        }

        // Giữ nguyên khu vực hiện tại khi client cũ cập nhật nhân viên mà chưa gửi trường mới.
        if (!creating && request.khuVucPhuTrach() == null) {
            return;
        }
        employee.setKhuVucPhuTrach(normalizeArea(request.khuVucPhuTrach()));
    }

    private String normalizeArea(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
