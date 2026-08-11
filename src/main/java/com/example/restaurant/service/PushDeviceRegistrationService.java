package com.example.restaurant.service;

import com.example.restaurant.dto.push.PushDeviceRegistrationRequest;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.PushDeviceRegistration;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.PushDeviceRegistrationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
public class PushDeviceRegistrationService {
    private static final Set<String> ALLOWED_CHANNELS = Set.of("KITCHEN", "WAITER");

    private final EmployeeRepository employeeRepository;
    private final PushDeviceRegistrationRepository repository;

    public PushDeviceRegistrationService(EmployeeRepository employeeRepository,
                                         PushDeviceRegistrationRepository repository) {
        this.employeeRepository = employeeRepository;
        this.repository = repository;
    }

    @Transactional
    public void register(String username, PushDeviceRegistrationRequest request) {
        Employee employee = employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));

        String channel = normalizeChannel(request.channel());
        authorizeChannel(employee, channel);
        String fid = request.installationId().trim();

        PushDeviceRegistration registration = repository
                .findByFirebaseInstallationIdAndChannel(fid, channel)
                .orElseGet(PushDeviceRegistration::new);
        registration.setEmployee(employee);
        registration.setFirebaseInstallationId(fid);
        registration.setChannel(channel);
        registration.setUserAgent(trimToNull(request.userAgent()));
        registration.setActive(true);
        repository.save(registration);
    }

    @Transactional
    public void unregister(String username, String installationId, String channel) {
        Employee employee = employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
        String normalizedChannel = normalizeChannel(channel);
        repository.deleteByFirebaseInstallationIdAndChannelAndEmployee_MaNhanVien(
                installationId == null ? "" : installationId.trim(),
                normalizedChannel,
                employee.getMaNhanVien()
        );
    }

    private void authorizeChannel(Employee employee, String channel) {
        String role = employee.getVaiTro() == null || employee.getVaiTro().getTenVaiTro() == null
                ? ""
                : employee.getVaiTro().getTenVaiTro().replace("ROLE_", "").toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(role) && !channel.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không được đăng ký kênh thông báo này");
        }
    }

    private String normalizeChannel(String value) {
        String channel = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CHANNELS.contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kênh push chỉ hỗ trợ KITCHEN hoặc WAITER");
        }
        return channel;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
