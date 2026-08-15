package com.example.restaurant.dto;

import java.math.BigDecimal;

public record DeliveryQuoteResponse(
        String phuongThucNhanHang,
        String tinhThanh,
        String quanHuyen,
        String phuongXa,
        String khuVucGiaoHang,
        String tenKhuVuc,
        BigDecimal phiGiaoHang,
        String diaChiDayDu,
        boolean googleMaps,
        String googlePlaceId,
        Integer quangDuongMet,
        Long thoiGianDuKienGiay,
        Long thoiGianNhanDuKienGiay,
        String encodedPolyline
) {
}
