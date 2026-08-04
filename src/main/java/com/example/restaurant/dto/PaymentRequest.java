package com.example.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Dữ liệu xác nhận thanh toán cuối cùng do frontend thu ngân gửi lên.
 *
 * Backend luôn xác định thu ngân từ JWT/Principal. Trường maNhanVien chỉ được
 * giữ lại để không làm hỏng các frontend cũ, nhưng không được sử dụng để gán
 * người thực hiện thanh toán.
 */
public record PaymentRequest(
        @NotNull(message = "Mã đơn hàng không được để trống")
        Integer maDonHang,

        Integer maNhanVien,

        @NotBlank(message = "Phương thức thanh toán không được để trống")
        String phuongThucThanhToan,

        @DecimalMin(value = "0.0", inclusive = false, message = "Tiền khách đưa phải lớn hơn 0")
        BigDecimal tienKhachDua,

        @Size(max = 100, message = "Mã giao dịch tối đa 100 ký tự")
        String maGiaoDich,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String ghiChu,

        @Size(max = 20, message = "Số điện thoại khách hàng tối đa 20 ký tự")
        String soDienThoaiKhachHang,

        @Size(max = 100, message = "Họ tên khách hàng tối đa 100 ký tự")
        String hoTenKhachHang,

        @Min(value = 0, message = "Số điểm sử dụng không được âm")
        Integer diemSuDung
) {
}
