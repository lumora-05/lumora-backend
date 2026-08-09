package com.example.restaurant.dto;

import jakarta.validation.constraints.Size;

public record DeliveryQuoteRequest(
        @Size(max = 100) String tinhThanh,
        @Size(max = 100) String quanHuyen,
        @Size(max = 120) String phuongXa,
        @Size(max = 500) String diaChiChiTiet,
        @Size(max = 255) String googlePlaceId,
        @Size(max = 700) String googleFormattedAddress
) {
}
