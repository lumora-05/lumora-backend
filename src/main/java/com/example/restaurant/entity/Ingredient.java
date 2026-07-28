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
        name = "nguyen_lieu",
        indexes = {
                @Index(name = "idx_nguyen_lieu_ten", columnList = "ten_nguyen_lieu"),
                @Index(name = "idx_nguyen_lieu_trang_thai", columnList = "trang_thai")
        }
)
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nguyen_lieu")
    private Integer maNguyenLieu;

    @Column(name = "ten_nguyen_lieu", length = 150, nullable = false)
    private String tenNguyenLieu;

    @Column(name = "don_vi_tinh", length = 30, nullable = false)
    private String donViTinh;

    @Column(name = "so_luong_ton", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuongTon = BigDecimal.ZERO;

    @Column(name = "muc_ton_toi_thieu", precision = 15, scale = 3, nullable = false)
    private BigDecimal mucTonToiThieu = BigDecimal.ZERO;

    @Column(name = "gia_nhap", precision = 18, scale = 2)
    private BigDecimal giaNhap;

    @Column(name = "mo_ta", length = 500)
    private String moTa;

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
        ngayCapNhat = now;
        normalizeNumbers();
    }

    @PreUpdate
    void preUpdate() {
        ngayCapNhat = LocalDateTime.now();
        normalizeNumbers();
    }

    private void normalizeNumbers() {
        if (soLuongTon == null) {
            soLuongTon = BigDecimal.ZERO;
        }
        if (mucTonToiThieu == null) {
            mucTonToiThieu = BigDecimal.ZERO;
        }
        if (trangThai == null) {
            trangThai = true;
        }
    }
}
