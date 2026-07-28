package com.example.restaurant.dto.dashboard;

import java.time.LocalDateTime;

public record RecentActivityResponse(
        Long maHoatDong,
        String loaiHoatDong,
        String noiDung,
        Integer doiTuongId,
        String nguoiThucHien,
        LocalDateTime thoiGian
) {
}
