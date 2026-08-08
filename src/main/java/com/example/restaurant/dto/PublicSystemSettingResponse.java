package com.example.restaurant.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Chỉ chứa các cấu hình an toàn và cần thiết cho website công khai. */
@Getter
@Builder
public class PublicSystemSettingResponse {
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
