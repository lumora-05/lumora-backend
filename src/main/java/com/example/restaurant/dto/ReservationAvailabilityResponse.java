package com.example.restaurant.dto;

public record ReservationAvailabilityResponse(
        Integer maBan,
        String tenBan,
        String khuVuc,
        Integer sucChua,
        String trangThai,
        boolean khaDung
) {}
