package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dữ liệu đầy đủ để frontend in phiếu tạm tính có VietQR.
 * Việc đọc DTO này không tạo hóa đơn và không đổi trạng thái đơn hàng.
 */
public record PaymentSlipResponse(
        Integer maDonHang,
        String maDonHangHienThi,
        Integer maBan,
        String tenBan,
        LocalDateTime thoiGianDat,
        LocalDateTime thoiGianYeuCauThanhToan,
        String nhanVienPhucVu,
        String maCodeKhuyenMai,
        BigDecimal tamTinh,
        BigDecimal tienGiam,
        BigDecimal tongTien,
        BigDecimal tienCocDaKhauTru,
        BigDecimal conLaiPhaiThanhToan,
        String trangThaiDonHang,
        List<PaymentSlipItemResponse> items,
        VietQrResponse vietQr,
        LocalDateTime thoiGianTaoPhieu,
        String loaiPhieu
) {
}
