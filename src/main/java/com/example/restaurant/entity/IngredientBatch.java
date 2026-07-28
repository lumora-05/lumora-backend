package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "lo_nguyen_lieu",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lo_nguyen_lieu_so_lo",
                        columnNames = {"ma_nguyen_lieu", "so_lo"}
                )
        },
        indexes = {
                @Index(name = "idx_lo_nguyen_lieu_nguyen_lieu", columnList = "ma_nguyen_lieu"),
                @Index(name = "idx_lo_nguyen_lieu_han_su_dung", columnList = "han_su_dung"),
                @Index(name = "idx_lo_nguyen_lieu_trang_thai", columnList = "trang_thai")
        }
)
public class IngredientBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_lo")
    private Long maLo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_nguyen_lieu", nullable = false)
    private Ingredient nguyenLieu;

    @Column(name = "so_lo", length = 80, nullable = false)
    private String soLo;

    @Column(name = "ngay_nhap", nullable = false)
    private LocalDate ngayNhap;

    @Column(name = "ngay_san_xuat")
    private LocalDate ngaySanXuat;

    @Column(name = "han_su_dung")
    private LocalDate hanSuDung;

    @Column(name = "so_luong_ban_dau", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuongBanDau = BigDecimal.ZERO;

    @Column(name = "so_luong_con_lai", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuongConLai = BigDecimal.ZERO;

    @Column(name = "don_gia_nhap", precision = 18, scale = 2)
    private BigDecimal donGiaNhap;

    @Column(name = "nha_cung_cap", length = 200)
    private String nhaCungCap;

    @Column(name = "trang_thai", nullable = false)
    private Boolean trangThai = true;

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao;

    @Column(name = "ngay_cap_nhat", nullable = false)
    private LocalDateTime ngayCapNhat;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (ngayTao == null) {
            ngayTao = now;
        }
        if (ngayNhap == null) {
            ngayNhap = LocalDate.now();
        }
        ngayCapNhat = now;
        normalizeValues();
    }

    @PreUpdate
    void preUpdate() {
        ngayCapNhat = LocalDateTime.now();
        normalizeValues();
    }

    private void normalizeValues() {
        if (soLuongBanDau == null) {
            soLuongBanDau = BigDecimal.ZERO;
        }
        if (soLuongConLai == null) {
            soLuongConLai = BigDecimal.ZERO;
        }
        if (trangThai == null) {
            trangThai = true;
        }
    }
}
