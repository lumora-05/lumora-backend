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
@Table(name = "hoa_don")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_hoa_don")
    private Integer maHoaDon;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_don_hang", nullable = false, unique = true)
    private Order donHang;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nhan_vien", nullable = false)
    private Employee nhanVien;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_khach_hang")
    private Customer khachHang;

    @Column(name = "tam_tinh", precision = 12, scale = 2)
    private BigDecimal tamTinh;

    @Column(name = "tien_giam", precision = 12, scale = 2)
    private BigDecimal tienGiam;

    @Column(name = "phi_giao_hang", precision = 12, scale = 2)
    private BigDecimal phiGiaoHang = BigDecimal.ZERO;

    @Column(name = "diem_da_su_dung")
    private Integer diemDaSuDung = 0;

    @Column(name = "tien_giam_tu_diem", precision = 12, scale = 2)
    private BigDecimal tienGiamTuDiem = BigDecimal.ZERO;

    @Column(name = "diem_duoc_cong")
    private Integer diemDuocCong = 0;

    @Column(name = "ma_code_khuyen_mai", length = 50)
    private String maCodeKhuyenMai;

    @Column(name = "tong_tien", precision = 12, scale = 2, nullable = false)
    private BigDecimal tongTien;

    /** Thời điểm bản ghi hóa đơn được tạo, giữ lại để tương thích dữ liệu cũ. */
    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    /** Thời điểm thu ngân xác nhận đã thực sự nhận tiền. */
    @Column(name = "thoi_gian_thanh_toan")
    private LocalDateTime thoiGianThanhToan;

    @Column(name = "phuong_thuc_thanh_toan", length = 50, nullable = false)
    private String phuongThucThanhToan;

    @Column(name = "trang_thai_thanh_toan", length = 20, nullable = false)
    private String trangThaiThanhToan = "DA_THANH_TOAN";

    @Column(name = "tien_khach_dua", precision = 12, scale = 2)
    private BigDecimal tienKhachDua;

    @Column(name = "tien_thua", precision = 12, scale = 2)
    private BigDecimal tienThua;

    @Column(name = "ma_giao_dich", length = 100, unique = true)
    private String maGiaoDich;

    @Column(name = "ghi_chu", length = 255)
    private String ghiChu;

    @Column(name = "noi_dung_chuyen_khoan", length = 50)
    private String noiDungChuyenKhoan;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (thoiGianTao == null) {
            thoiGianTao = now;
        }
        if (thoiGianThanhToan == null) {
            thoiGianThanhToan = now;
        }
        if (trangThaiThanhToan == null || trangThaiThanhToan.isBlank()) {
            trangThaiThanhToan = "DA_THANH_TOAN";
        }
        initializeLoyaltyDefaults();
    }

    @PostLoad
    void postLoad() {
        initializeLoyaltyDefaults();
    }

    private void initializeLoyaltyDefaults() {
        if (phiGiaoHang == null || phiGiaoHang.signum() < 0) {
            phiGiaoHang = BigDecimal.ZERO;
        }
        if (diemDaSuDung == null || diemDaSuDung < 0) {
            diemDaSuDung = 0;
        }
        if (tienGiamTuDiem == null || tienGiamTuDiem.signum() < 0) {
            tienGiamTuDiem = BigDecimal.ZERO;
        }
        if (diemDuocCong == null || diemDuocCong < 0) {
            diemDuocCong = 0;
        }
    }
}
