package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.CustomerAccountResponse;
import com.example.restaurant.dto.CustomerAuthResponse;
import com.example.restaurant.dto.CustomerLoginRequest;
import com.example.restaurant.dto.CustomerRegisterRequest;
import com.example.restaurant.dto.DeliveryTrackingResponse;
import com.example.restaurant.entity.Customer;
import com.example.restaurant.service.CustomerAccountService;
import com.example.restaurant.service.DeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/account")
public class CustomerAccountController {
    private final CustomerAccountService customerAccountService;
    private final DeliveryOrderService deliveryOrderService;

    public CustomerAccountController(CustomerAccountService customerAccountService,
                                     DeliveryOrderService deliveryOrderService) {
        this.customerAccountService = customerAccountService;
        this.deliveryOrderService = deliveryOrderService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CustomerAuthResponse>> register(
            @Valid @RequestBody CustomerRegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đăng ký tài khoản khách hàng thành công",
                customerAccountService.register(request)
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<CustomerAuthResponse>> login(
            @Valid @RequestBody CustomerLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đăng nhập khách hàng thành công",
                customerAccountService.login(request)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerAccountResponse>> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Customer customer = customerAccountService.requireCustomer(authorization);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin tài khoản thành công",
                customerAccountService.toAccountResponse(customer)
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<DeliveryTrackingResponse>>> myOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Customer customer = customerAccountService.requireCustomer(authorization);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử đơn hàng thành công",
                deliveryOrderService.findByCustomer(customer.getMaKhachHang())
        ));
    }
}
