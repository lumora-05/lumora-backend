package com.example.restaurant.dto;

import com.example.restaurant.entity.Category;

public record CategoryResponse(
        Integer maDanhMuc,
        String tenDanhMuc,
        String moTa,
        Boolean trangThai,
        long soMon
) {
    public static CategoryResponse from(Category category, long soMon) {
        return new CategoryResponse(
                category.getMaDanhMuc(),
                category.getTenDanhMuc(),
                category.getMoTa(),
                category.getTrangThai(),
                soMon
        );
    }
}
