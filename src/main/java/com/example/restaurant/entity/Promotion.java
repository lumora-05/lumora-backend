package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "khuyen_mai")
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_khuyen_mai")
    private Integer maKhuyenMai;

    @Column(name = "ma_code", length = 50, unique = true)
    private String maCode;

    @Column(name = "ten_khuyen_mai", length = 100)
    private String tenKhuyenMai;

    @Column(name = "ten_khuyen_mai_en", length = 150)
    private String tenKhuyenMaiEn;

    @Column(name = "mo_ta", length = 255)
    private String moTa;

    @Column(name = "mo_ta_en", length = 500)
    private String moTaEn;

    /** PERCENT hoặc FIXED. */
    @Column(name = "loai_giam", length = 30)
    private String loaiGiam;

    @Column(name = "gia_tri_giam", precision = 12, scale = 2)
    private BigDecimal giaTriGiam;

    /** Tổng tiền món tối thiểu để mã được áp dụng. */
    @Column(name = "gia_tri_don_toi_thieu", precision = 12, scale = 2)
    private BigDecimal giaTriDonToiThieu = BigDecimal.ZERO;

    /** Mức giảm tối đa, chủ yếu dùng cho mã phần trăm. Null nghĩa là không giới hạn. */
    @Column(name = "giam_toi_da", precision = 12, scale = 2)
    private BigDecimal giamToiDa;

    /** Tổng số lượt được áp dụng. Null nghĩa là không giới hạn. */
    @Column(name = "gioi_han_su_dung")
    private Integer gioiHanSuDung;

    /** Số lượt hiện đã được giữ chỗ/áp dụng cho đơn hàng. */
    @Column(name = "so_luot_da_dung")
    private Integer soLuotDaDung = 0;

    @Column(name = "ngay_bat_dau")
    private LocalDate ngayBatDau;

    @Column(name = "ngay_ket_thuc")
    private LocalDate ngayKetThuc;

    @Column(name = "trang_thai")
    private Boolean trangThai = true;

    @PrePersist
    @PreUpdate
    void initializeDefaults() {
        if (giaTriDonToiThieu == null) {
            giaTriDonToiThieu = BigDecimal.ZERO;
        }
        if (soLuotDaDung == null || soLuotDaDung < 0) {
            soLuotDaDung = 0;
        }
        if (trangThai == null) {
            trangThai = true;
        }
    }

    @PostLoad
    void initializeLoadedDefaults() {
        initializeDefaults();
    }
}
