package com.example.restaurant.dto;

public record AuthResponse(
        String token,
        String username,
        String role,
        String fullName,
        Integer maNhanVien,
        String anhDaiDien,
        Integer maKhachHang,
        String soDienThoai,
        Integer diemTichLuy
) {
}
