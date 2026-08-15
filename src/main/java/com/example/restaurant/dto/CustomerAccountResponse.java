package com.example.restaurant.dto;

public record CustomerAccountResponse(
        Integer maKhachHang,
        String hoTen,
        String soDienThoai,
        Integer diemTichLuy
) {
}
