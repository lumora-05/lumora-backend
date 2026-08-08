package com.example.restaurant.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SystemSettingResponse {
    private Integer id;
    private String restaurantName;
    private String address;
    private String phone;
    private String email;
    private String openingHours;
    private String reservationUrl;
    private String menuUrl;
    private String logoUrl;
    private String bannerUrl;
    private LocalDateTime updatedAt;
}
