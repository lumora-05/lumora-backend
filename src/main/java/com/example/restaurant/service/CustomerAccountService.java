package com.example.restaurant.service;

import com.example.restaurant.dto.CustomerAccountResponse;
import com.example.restaurant.dto.CustomerAuthResponse;
import com.example.restaurant.dto.CustomerLoginRequest;
import com.example.restaurant.dto.CustomerRegisterRequest;
import com.example.restaurant.dto.CustomerProfileUpdateRequest;
import com.example.restaurant.entity.Customer;
import com.example.restaurant.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class CustomerAccountService {
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public CustomerAccountService(CustomerRepository customerRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtService jwtService) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public CustomerAuthResponse register(CustomerRegisterRequest request) {
        String phone = normalizePhone(request.soDienThoai());
        Customer customer = customerRepository.findBySoDienThoaiForUpdate(phone).orElse(null);

        if (customer != null && StringUtils.hasText(customer.getMatKhauHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã có tài khoản");
        }
        if (customer != null && !"HOAT_DONG".equalsIgnoreCase(customer.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản khách hàng đã ngừng hoạt động");
        }

        if (customer == null) {
            customer = new Customer();
            customer.setSoDienThoai(phone);
            customer.setDiemTichLuy(0);
            customer.setTrangThai("HOAT_DONG");
        }

        customer.setHoTen(requiredText(request.hoTen(), "Họ tên không hợp lệ"));
        customer.setMatKhauHash(passwordEncoder.encode(request.matKhau()));
        Customer saved = customerRepository.saveAndFlush(customer);
        return toAuthResponse(saved);
    }

    @Transactional
    public CustomerAuthResponse updateProfile(String authorizationHeader, CustomerProfileUpdateRequest request) {
        Integer customerId = requireCustomerId(authorizationHeader);
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản khách hàng không còn tồn tại"));

        if (!"HOAT_DONG".equalsIgnoreCase(customer.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản khách hàng đã ngừng hoạt động");
        }

        String newName = requiredText(request.hoTen(), "Họ tên không hợp lệ");
        String newPhone = normalizePhone(request.soDienThoai());

        if (!newPhone.equals(customer.getSoDienThoai())) {
            Customer existing = customerRepository.findBySoDienThoaiForUpdate(newPhone).orElse(null);
            if (existing != null && !existing.getMaKhachHang().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
            }
        }

        customer.setHoTen(newName);
        customer.setSoDienThoai(newPhone);
        Customer saved = customerRepository.saveAndFlush(customer);

        // Cấp lại JWT để claim số điện thoại luôn đồng bộ khi khách đổi số.
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerAuthResponse login(CustomerLoginRequest request) {
        String phone = normalizePhone(request.soDienThoai());
        Customer customer = customerRepository.findBySoDienThoai(phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Số điện thoại hoặc mật khẩu không đúng"));

        if (!StringUtils.hasText(customer.getMatKhauHash())
                || !passwordEncoder.matches(request.matKhau(), customer.getMatKhauHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Số điện thoại hoặc mật khẩu không đúng");
        }
        if (!"HOAT_DONG".equalsIgnoreCase(customer.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản khách hàng đã ngừng hoạt động");
        }
        return toAuthResponse(customer);
    }

    @Transactional(readOnly = true)
    public Customer requireCustomer(String authorizationHeader) {
        Integer customerId = requireCustomerId(authorizationHeader);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản khách hàng không còn tồn tại"));
        if (!"HOAT_DONG".equalsIgnoreCase(customer.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản khách hàng đã ngừng hoạt động");
        }
        return customer;
    }

    @Transactional(readOnly = true)
    public Customer resolveOptionalCustomer(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        if (!"CUSTOMER".equalsIgnoreCase(jwtService.extractTokenType(token))) {
            return null;
        }
        return requireCustomer(authorizationHeader);
    }

    public CustomerAccountResponse toAccountResponse(Customer customer) {
        return new CustomerAccountResponse(
                customer.getMaKhachHang(),
                customer.getHoTen(),
                customer.getSoDienThoai(),
                customer.getDiemTichLuy()
        );
    }

    private CustomerAuthResponse toAuthResponse(Customer customer) {
        return new CustomerAuthResponse(
                jwtService.generateCustomerToken(customer.getMaKhachHang(), customer.getSoDienThoai()),
                customer.getMaKhachHang(),
                customer.getHoTen(),
                customer.getSoDienThoai(),
                customer.getDiemTichLuy()
        );
    }

    private Integer requireCustomerId(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập tài khoản khách hàng");
        }
        String token = authorizationHeader.substring(7).trim();
        if (!"CUSTOMER".equalsIgnoreCase(jwtService.extractTokenType(token))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token khách hàng không hợp lệ");
        }
        Integer customerId = jwtService.extractCustomerId(token);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token khách hàng không hợp lệ");
        }
        return customerId;
    }

    private String normalizePhone(String raw) {
        String phone = requiredText(raw, "Số điện thoại không hợp lệ").replaceAll("[^0-9+]", "");
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        } else if (phone.startsWith("84") && phone.length() >= 11) {
            phone = "0" + phone.substring(2);
        }
        if (!phone.matches("^0[0-9]{8,10}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        return phone.toLowerCase(Locale.ROOT);
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
