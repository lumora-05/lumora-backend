package com.example.restaurant.dto;

public record ProfileResponse(
        Integer maNhanVien,
        String hoTen,
        String email,
        String soDienThoai,
        String tenDangNhap,
        String tenVaiTro,
        String role,
        String trangThai,
        String anhDaiDien
) {
}
