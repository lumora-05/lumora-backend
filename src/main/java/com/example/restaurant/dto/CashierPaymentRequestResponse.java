package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dòng dữ liệu tối giản cho trang Thanh toán của thu ngân.
 * Cấu trúc banAn được giữ tương thích với các helper hiện tại của frontend.
 */
public record CashierPaymentRequestResponse(
        Integer maDonHang,
        TableSummary banAn,
        Long soMon,
        BigDecimal tongTien,
        String trangThai,
        LocalDateTime thoiGianYeuCauThanhToan,
        LocalDateTime thoiGianCapNhat,
        LocalDateTime thoiGianDat,
        String maNhomThanhToan
) {
    public record TableSummary(
            Integer maBan,
            String tenBan,
            Integer maBanChinh
    ) {
    }
}
