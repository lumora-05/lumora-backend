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
        name = "giao_dich_kho",
        indexes = {
                @Index(name = "idx_giao_dich_kho_nguyen_lieu", columnList = "ma_nguyen_lieu"),
                @Index(name = "idx_giao_dich_kho_lo", columnList = "ma_lo"),
                @Index(name = "idx_giao_dich_kho_loai", columnList = "loai_giao_dich"),
                @Index(name = "idx_giao_dich_kho_ma_ly_do", columnList = "ma_ly_do"),
                @Index(name = "idx_giao_dich_kho_thoi_gian", columnList = "thoi_gian"),
                @Index(name = "idx_giao_dich_kho_chi_tiet", columnList = "ma_chi_tiet")
        }
)
public class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_giao_dich")
    private Long maGiaoDich;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_nguyen_lieu", nullable = false)
    private Ingredient nguyenLieu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_lo")
    private IngredientBatch loNguyenLieu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_chi_tiet")
    private OrderItem chiTietDonHang;

    @Column(name = "loai_giao_dich", length = 30, nullable = false)
    private String loaiGiaoDich;

    @Column(name = "so_luong", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuong;

    @Column(name = "so_luong_truoc", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuongTruoc;

    @Column(name = "so_luong_sau", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuongSau;

    @Column(name = "don_gia_nhap", precision = 18, scale = 2)
    private BigDecimal donGiaNhap;

    @Column(name = "ly_do", length = 500)
    private String lyDo;

    @Column(name = "ma_ly_do", length = 40)
    private String maLyDo;

    @Column(name = "ghi_chu", length = 500)
    private String ghiChu;

    @Column(name = "nguoi_thuc_hien", length = 100, nullable = false)
    private String nguoiThucHien;

    @Column(name = "thoi_gian", nullable = false)
    private LocalDateTime thoiGian;

    @PrePersist
    void prePersist() {
        if (thoiGian == null) {
            thoiGian = LocalDateTime.now();
        }
    }
}
