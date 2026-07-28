package com.example.restaurant.dto;

import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Order;

import java.util.List;

public record TableArrangementResponse(
        String loaiThaoTac,
        String maNhomBan,
        DiningTable banChinh,
        List<DiningTable> cacBan,
        Order donHang
) {
}
