package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.AuthRequest;
import com.example.restaurant.dto.AuthResponse;
import com.example.restaurant.dto.ForgotPasswordResetRequest;
import com.example.restaurant.dto.ForgotPasswordSendCodeRequest;
import com.example.restaurant.dto.ForgotPasswordSendCodeResponse;
import com.example.restaurant.dto.ForgotPasswordVerifyCodeRequest;
import com.example.restaurant.dto.ForgotPasswordVerifyCodeResponse;
import com.example.restaurant.dto.GoogleLoginRequest;
import com.example.restaurant.service.AuthService;
import com.example.restaurant.service.ForgotPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final ForgotPasswordService forgotPasswordService;

    public AuthController(AuthService authService,
                          ForgotPasswordService forgotPasswordService) {
        this.authService = authService;
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập Google thành công", response));
    }

    @PostMapping("/forgot-password/send-code")
    public ResponseEntity<ApiResponse<ForgotPasswordSendCodeResponse>> sendResetCode(
            @Valid @RequestBody ForgotPasswordSendCodeRequest request) {
        ForgotPasswordSendCodeResponse response = forgotPasswordService.sendCode(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email tồn tại trong hệ thống, mã xác nhận đã được gửi",
                response
        ));
    }

    @PostMapping("/forgot-password/verify-code")
    public ResponseEntity<ApiResponse<ForgotPasswordVerifyCodeResponse>> verifyResetCode(
            @Valid @RequestBody ForgotPasswordVerifyCodeRequest request) {
        ForgotPasswordVerifyCodeResponse response = forgotPasswordService.verifyCode(request);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận mã thành công", response));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        forgotPasswordService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công"));
    }
}
