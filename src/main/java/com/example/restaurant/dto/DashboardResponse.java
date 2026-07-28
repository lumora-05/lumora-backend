package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        long tongBan,
        long banTrong,
        long banDangSuDung,
        long tongMonDangBan,
        long donHomNay,
        long donChoXacNhan,
        long donDangCheBien,
        long donDaThanhToan,
        BigDecimal doanhThuHomNay,
        List<RevenueByDayResponse> doanhThu7NgayGanNhat
) {
}
