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
        name = "khoan_thanh_toan",
        indexes = {
                @Index(name = "idx_khoan_thanh_toan_don_hang", columnList = "ma_don_hang"),
                @Index(name = "idx_khoan_thanh_toan_phuong_thuc", columnList = "phuong_thuc_thanh_toan")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_khoan_thanh_toan_payos", columnNames = "ma_giao_dich_payos")
        }
)
public class PaymentPart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_khoan_thanh_toan")
    private Long maKhoanThanhToan;

    /** Đơn chính đại diện cho bill, kể cả khi nhiều bàn đang thanh toán chung. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_don_hang", nullable = false)
    private Order donHang;

    /** Nhân viên ghi nhận khoản tiền; với payOS là người đã mở QR tại quầy. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nhan_vien")
    private Employee nhanVien;

    /** Có giá trị với khoản chuyển khoản payOS, giúp webhook được xử lý idempotent. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_giao_dich_payos", unique = true)
    private PayOsPayment giaoDichPayOs;

    @Column(name = "phuong_thuc_thanh_toan", length = 30, nullable = false)
    private String phuongThucThanhToan;

    /** Số tiền thực sự được tính vào bill, không phải tiền khách đưa trước khi trả lại tiền thừa. */
    @Column(name = "so_tien", precision = 12, scale = 2, nullable = false)
    private BigDecimal soTien;

    @Column(name = "ma_giao_dich", length = 100)
    private String maGiaoDich;

    @Column(name = "ghi_chu", length = 255)
    private String ghiChu;

    /** Giữ ngữ cảnh tích điểm xuyên suốt một phiên thanh toán kết hợp. */
    @Column(name = "so_dien_thoai_khach", length = 20)
    private String soDienThoaiKhach;

    @Column(name = "ho_ten_khach_hang", length = 100)
    private String hoTenKhachHang;

    @Column(name = "diem_su_dung", nullable = false)
    private Integer diemSuDung = 0;

    @Column(name = "thoi_gian_thanh_toan", nullable = false)
    private LocalDateTime thoiGianThanhToan = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        if (diemSuDung == null || diemSuDung < 0) {
            diemSuDung = 0;
        }
        if (thoiGianThanhToan == null) {
            thoiGianThanhToan = LocalDateTime.now();
        }
    }
}
