package com.example.restaurant.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderItemTraceResponse(
        Integer maChiTiet,
        Integer maDonHang,
        Integer maBan,
        String tenBan,
        Integer maMonAn,
        String tenMonAn,
        Integer soLuongMon,
        String trangThaiMon,
        LocalDateTime thoiGianDat,
        boolean coCongThuc,
        boolean daCapPhatNguyenLieu,
        List<OrderItemBatchUsageResponse> cacLoDaSuDung
) {
}
