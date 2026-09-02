package com.example.restaurant.service;

import com.example.restaurant.dto.AuthRequest;
import com.example.restaurant.dto.AuthResponse;
import com.example.restaurant.dto.CustomerAuthResponse;
import com.example.restaurant.dto.CustomerLoginRequest;
import com.example.restaurant.dto.GoogleLoginRequest;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.repository.EmployeeRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AuthService {
    private static final String ACTIVE_STATUS = "DANG_LAM_VIEC";
    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDetailsService employeeDetailsService;
    private final CustomerAccountService customerAccountService;
    private final JwtService jwtService;
    private final GoogleTokenService googleTokenService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager,
                       EmployeeRepository employeeRepository,
                       EmployeeDetailsService employeeDetailsService,
                       CustomerAccountService customerAccountService,
                       JwtService jwtService,
                       GoogleTokenService googleTokenService,
                       LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.employeeRepository = employeeRepository;
        this.employeeDetailsService = employeeDetailsService;
        this.customerAccountService = customerAccountService;
        this.jwtService = jwtService;
        this.googleTokenService = googleTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Điểm đăng nhập dùng chung cho 5 vai trò của hệ thống.
     * - Nhân viên (ADMIN/WAITER/KITCHEN/CASHIER): đăng nhập bằng tên đăng nhập.
     * - CUSTOMER: đăng nhập bằng số điện thoại.
     *
     * API khách hàng cũ vẫn được giữ nguyên để không làm hỏng frontend hiện tại.
     */
    public AuthResponse login(AuthRequest request) {
        String identifier = request.username().trim();

        Optional<Employee> employee = employeeRepository.findByTenDangNhap(identifier);
        if (employee.isPresent()) {
            loginAttemptService.assertAllowed("EMPLOYEE", identifier);
            try {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(identifier, request.password())
                );
            } catch (BadCredentialsException ex) {
                loginAttemptService.recordFailure("EMPLOYEE", identifier);
                loginAttemptService.assertAllowed("EMPLOYEE", identifier);
                throw ex;
            }
            loginAttemptService.reset("EMPLOYEE", identifier);
            return createEmployeeAuthResponse(employee.get());
        }

        if (!looksLikePhone(identifier)) {
            loginAttemptService.assertAllowed("EMPLOYEE", identifier);
            loginAttemptService.recordFailure("EMPLOYEE", identifier);
            loginAttemptService.assertAllowed("EMPLOYEE", identifier);
            throw new BadCredentialsException("Thông tin đăng nhập không đúng");
        }

        try {
            CustomerAuthResponse customer = customerAccountService.login(
                    new CustomerLoginRequest(identifier, request.password())
            );
            return createCustomerAuthResponse(customer);
        } catch (ResponseStatusException ex) {
            // Giữ nguyên thông báo tài khoản khách hàng đã bị ngừng hoạt động.
            if (ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                    || ex.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw ex;
            }

            // Không tiết lộ tài khoản/SĐT có tồn tại hay không tại endpoint đăng nhập chung.
            if (ex.getStatusCode().value() >= 400 && ex.getStatusCode().value() < 500) {
                throw new BadCredentialsException("Thông tin đăng nhập không đúng");
            }
            throw ex;
        }
    }

    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleTokenService.verify(request.credential());
        String email = payload.getEmail().trim();

        Employee employee = employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Email Google chưa được cấp quyền sử dụng hệ thống"
                ));

        if (!ACTIVE_STATUS.equalsIgnoreCase(employee.getTrangThai())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản nhân viên đã ngừng hoạt động"
            );
        }

        return createEmployeeAuthResponse(employee);
    }

    private AuthResponse createEmployeeAuthResponse(Employee employee) {
        UserDetails userDetails = employeeDetailsService.loadUserByUsername(employee.getTenDangNhap());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                employee.getTenDangNhap(),
                employee.getVaiTro().getTenVaiTro(),
                employee.getHoTen(),
                employee.getMaNhanVien(),
                employee.getAnhDaiDien(),
                null,
                null,
                null
        );
    }

    private AuthResponse createCustomerAuthResponse(CustomerAuthResponse customer) {
        return new AuthResponse(
                customer.token(),
                customer.soDienThoai(),
                CUSTOMER_ROLE,
                customer.hoTen(),
                null,
                null,
                customer.maKhachHang(),
                customer.soDienThoai(),
                customer.diemTichLuy()
        );
    }

    private boolean looksLikePhone(String value) {
        return value != null && value.matches("^[0-9+ .()\\-]{9,20}$");
    }
}
