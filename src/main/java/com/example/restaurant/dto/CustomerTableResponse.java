package com.example.restaurant.dto;

import com.example.restaurant.entity.Category;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Food;

import java.util.List;

public record CustomerTableResponse(
        DiningTable banAn,
        List<Category> danhMuc,
        List<Food> thucDon
) {
}
