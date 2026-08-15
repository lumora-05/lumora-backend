package com.example.restaurant.dto;

import jakarta.validation.constraints.Size;

public record DeliveryQuoteRequest(
        /** GIAO_TAN_NOI (mặc định để tương thích client cũ) hoặc TU_DEN_LAY. */
        @Size(max = 20) String phuongThucNhanHang,
        @Size(max = 100) String tinhThanh,
        /** Trường tương thích với client cũ; không còn bắt buộc trong mô hình địa chỉ 2 cấp. */
        @Size(max = 100) String quanHuyen,
        @Size(max = 120) String phuongXa,
        @Size(max = 50) String soNha,
        @Size(max = 200) String tenDuong,
        @Size(max = 500) String thongTinDiaChi,
        /** Trường tương thích với client cũ; client mới dùng soNha + tenDuong. */
        @Size(max = 500) String diaChiChiTiet,
        @Size(max = 255) String googlePlaceId,
        @Size(max = 700) String googleFormattedAddress,
        /** Token ngắn hạn do backend cấp khi khách chọn một gợi ý địa chỉ. */
        @Size(max = 4000) String addressSelectionToken
) {
}
