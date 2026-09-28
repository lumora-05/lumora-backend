package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Ghi nhận một phần tiền mặt của bill thanh toán kết hợp. */
public record MixedCashPaymentRequest(
        @NotNull(message = "Mã đơn hàng không được để trống")
        Integer maDonHang,

        @NotNull(message = "Số tiền mặt không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền mặt phải lớn hơn 0")
        BigDecimal soTien,

        @Size(max = 20, message = "Số điện thoại khách hàng tối đa 20 ký tự")
        String soDienThoaiKhachHang,

        @Size(max = 100, message = "Họ tên khách hàng tối đa 100 ký tự")
        String hoTenKhachHang,

        @Min(value = 0, message = "Số điểm sử dụng không được âm")
        Integer diemSuDung,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String ghiChu
) {
}
