package com.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record EmployeeRequest(
        @NotBlank String hoTen,
        String soDienThoai,
        String email,
        @NotBlank String tenDangNhap,
        String matKhau,
        @NotBlank String tenVaiTro,
        String trangThai,
        @Size(max = 100, message = "Khu vực phụ trách tối đa 100 ký tự")
        String khuVucPhuTrach,
        Set<@Size(max = 100, message = "Mỗi khu vực phụ trách tối đa 100 ký tự") String> danhSachKhuVucPhuTrach
) {
}
