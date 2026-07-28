package com.example.restaurant.service;

import com.example.restaurant.dto.AuthRequest;
import com.example.restaurant.dto.AuthResponse;
import com.example.restaurant.dto.GoogleLoginRequest;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.repository.EmployeeRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final String ACTIVE_STATUS = "DANG_LAM_VIEC";

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDetailsService employeeDetailsService;
    private final JwtService jwtService;
    private final GoogleTokenService googleTokenService;

    public AuthService(AuthenticationManager authenticationManager,
                       EmployeeRepository employeeRepository,
                       EmployeeDetailsService employeeDetailsService,
                       JwtService jwtService,
                       GoogleTokenService googleTokenService) {
        this.authenticationManager = authenticationManager;
        this.employeeRepository = employeeRepository;
        this.employeeDetailsService = employeeDetailsService;
        this.jwtService = jwtService;
        this.googleTokenService = googleTokenService;
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        Employee employee = employeeRepository.findByTenDangNhap(request.username())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        return createAuthResponse(employee);
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

        return createAuthResponse(employee);
    }

    private AuthResponse createAuthResponse(Employee employee) {
        UserDetails userDetails = employeeDetailsService.loadUserByUsername(employee.getTenDangNhap());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                employee.getTenDangNhap(),
                employee.getVaiTro().getTenVaiTro(),
                employee.getHoTen(),
                employee.getMaNhanVien(),
                employee.getAnhDaiDien()
        );
    }
}
