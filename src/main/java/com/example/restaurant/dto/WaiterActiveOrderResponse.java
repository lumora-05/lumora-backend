package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dữ liệu tối giản cho màn hình Đơn cần xử lý của phục vụ.
 * Giữ tên trường banAn/chiTietDonHang tương thích với helper frontend hiện tại,
 * nhưng không serialize toàn bộ graph Order -> Food -> Category -> Employee...
 */
public record WaiterActiveOrderResponse(
        Integer maDonHang,
        TableSummary banAn,
        String trangThai,
        LocalDateTime thoiGianDat,
        LocalDateTime thoiGianCapNhat,
        LocalDateTime thoiGianSanSang,
        LocalDateTime thoiGianYeuCauThanhToan,
        BigDecimal tongTien,
        String maNhomThanhToan,
        List<ItemSummary> chiTietDonHang
) {
    public record TableSummary(
            Integer maBan,
            String tenBan
    ) {
    }

    public record ItemSummary(
            Integer maChiTiet,
            Integer soLuong,
            String trangThaiMon,
            String tenMonAn,
            Integer lanGoi,
            LocalDateTime thoiGianThem
    ) {
    }
}
