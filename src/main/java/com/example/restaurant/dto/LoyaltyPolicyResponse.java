package com.example.restaurant.dto;

import java.math.BigDecimal;

public record LoyaltyPolicyResponse(
        BigDecimal soTienDeNhanMotDiem,
        BigDecimal giaTriMotDiem,
        Integer diemToiThieuDeDoi,
        BigDecimal tyLeThanhToanToiDaBangDiem
) {
}
