package com.example.restaurant.dto;

public record CustomerAuthResponse(
        String token,
        Integer maKhachHang,
        String hoTen,
        String soDienThoai,
        Integer diemTichLuy
) {
}
