package com.example.restaurant.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.repository.EmployeeRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class EmployeeDetailsService implements UserDetailsService {
    private final EmployeeRepository employeeRepository;

    public EmployeeDetailsService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Employee employee = employeeRepository.findByTenDangNhap(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên: " + username));
        String role = employee.getVaiTro().getTenVaiTro().replace("ROLE_", "");
        boolean active = "DANG_LAM_VIEC".equals(employee.getTrangThai());
        return User.builder()
                .username(employee.getTenDangNhap())
                .password(employee.getMatKhau())
                .roles(role)
                .disabled(!active)
                .build();
    }
}
