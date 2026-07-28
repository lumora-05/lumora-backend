package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.ChangePasswordRequest;
import com.example.restaurant.dto.ProfileResponse;
import com.example.restaurant.dto.ProfileUpdateRequest;
import com.example.restaurant.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tai-khoan")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/ho-so")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Authentication authentication) {
        ProfileResponse profile = accountService.getProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin cá nhân thành công", profile));
    }

    @PutMapping("/ho-so")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {
        ProfileResponse profile = accountService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin cá nhân thành công", profile));
    }

    @PostMapping(value = "/ho-so/anh-dai-dien", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        ProfileResponse profile = accountService.updateAvatar(authentication.getName(), file);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh đại diện thành công", profile));
    }

    @DeleteMapping("/ho-so/anh-dai-dien")
    public ResponseEntity<ApiResponse<ProfileResponse>> deleteAvatar(Authentication authentication) {
        ProfileResponse profile = accountService.deleteAvatar(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Xóa ảnh đại diện thành công", profile));
    }

    @PutMapping("/doi-mat-khau")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));
    }
}
