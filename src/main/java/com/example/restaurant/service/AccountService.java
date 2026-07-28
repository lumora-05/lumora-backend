package com.example.restaurant.service;

import com.example.restaurant.dto.ChangePasswordRequest;
import com.example.restaurant.dto.ProfileResponse;
import com.example.restaurant.dto.ProfileUpdateRequest;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    public AccountService(EmployeeRepository employeeRepository,
                          FileStorageService fileStorageService,
                          PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        return toResponse(findByUsername(username));
    }

    @Transactional
    public ProfileResponse updateProfile(String username, ProfileUpdateRequest request) {
        Employee employee = findByUsername(username);

        String email = normalizeNullable(request.email());
        if (email != null && employeeRepository.existsByEmailIgnoreCaseAndMaNhanVienNot(
                email,
                employee.getMaNhanVien())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác");
        }

        employee.setHoTen(request.hoTen().trim());
        employee.setEmail(email);
        employee.setSoDienThoai(normalizeNullable(request.soDienThoai()));

        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public ProfileResponse updateAvatar(String username, MultipartFile file) {
        Employee employee = findByUsername(username);
        String oldAvatar = employee.getAnhDaiDien();
        String newAvatar = fileStorageService.saveAvatarImage(file);

        try {
            employee.setAnhDaiDien(newAvatar);
            Employee saved = employeeRepository.save(employee);
            deleteAvatarAfterCommit(oldAvatar);
            return toResponse(saved);
        } catch (RuntimeException ex) {
            // Nếu lưu database thất bại thì dọn file mới để không tạo ảnh rác.
            fileStorageService.deleteAvatarImage(newAvatar);
            throw ex;
        }
    }

    @Transactional
    public ProfileResponse deleteAvatar(String username) {
        Employee employee = findByUsername(username);
        String oldAvatar = employee.getAnhDaiDien();

        employee.setAnhDaiDien(null);
        Employee saved = employeeRepository.save(employee);
        deleteAvatarAfterCommit(oldAvatar);

        return toResponse(saved);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        Employee employee = findByUsername(username);

        if (!passwordEncoder.matches(request.matKhauHienTai(), employee.getMatKhau())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        if (!request.matKhauMoi().equals(request.xacNhanMatKhauMoi())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới và xác nhận mật khẩu không khớp");
        }
        if (passwordEncoder.matches(request.matKhauMoi(), employee.getMatKhau())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được giống mật khẩu hiện tại");
        }

        employee.setMatKhau(passwordEncoder.encode(request.matKhauMoi()));
        employeeRepository.save(employee);
    }

    private void deleteAvatarAfterCommit(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorageService.deleteAvatarImage(imageUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.deleteAvatarImage(imageUrl);
            }
        });
    }

    private Employee findByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản đăng nhập");
        }
        return employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản nhân viên"));
    }

    private ProfileResponse toResponse(Employee employee) {
        String role = employee.getVaiTro() == null ? null : employee.getVaiTro().getTenVaiTro();
        return new ProfileResponse(
                employee.getMaNhanVien(),
                employee.getHoTen(),
                employee.getEmail(),
                employee.getSoDienThoai(),
                employee.getTenDangNhap(),
                role,
                role,
                employee.getTrangThai(),
                employee.getAnhDaiDien()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
