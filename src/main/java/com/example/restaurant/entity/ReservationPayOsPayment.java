package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "giao_dich_payos_dat_ban",
        indexes = {
                @Index(name = "idx_payos_dat_ban", columnList = "ma_dat_ban"),
                @Index(name = "idx_payos_dat_ban_trang_thai", columnList = "trang_thai")
        }
)
public class ReservationPayOsPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_giao_dich_payos_dat_ban")
    private Long maGiaoDichPayOsDatBan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_dat_ban", nullable = false)
    private TableReservation datBan;

    @Column(name = "payos_order_code", nullable = false, unique = true)
    private Long payOsOrderCode;

    @Column(name = "payment_link_id", length = 64, unique = true)
    private String paymentLinkId;

    @Column(name = "so_tien", precision = 12, scale = 2, nullable = false)
    private BigDecimal soTien;

    @Column(name = "so_dien_thoai_khach", length = 20)
    private String soDienThoaiKhach;

    @Column(name = "noi_dung_chuyen_khoan", length = 25, nullable = false)
    private String noiDungChuyenKhoan;

    @Column(name = "bin_ngan_hang", length = 20)
    private String binNganHang;

    @Column(name = "so_tai_khoan", length = 64)
    private String soTaiKhoan;

    @Column(name = "ten_tai_khoan", length = 160)
    private String tenTaiKhoan;

    @Column(name = "qr_code", length = 2500)
    private String qrCode;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    /** PENDING, PAID, CANCELLED, EXPIRED. */
    @Column(name = "trang_thai", length = 20, nullable = false)
    private String trangThai = "PENDING";

    /** Mã tham chiếu giao dịch ngân hàng do payOS gửi trong webhook. */
    @Column(name = "ma_tham_chieu", length = 100, unique = true)
    private String maThamChieu;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "het_han_luc")
    private LocalDateTime hetHanLuc;

    @Column(name = "thoi_gian_thanh_toan")
    private LocalDateTime thoiGianThanhToan;
}
