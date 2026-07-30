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
        name = "su_dung_lo_nguyen_lieu",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_su_dung_lo_chi_tiet_nguyen_lieu_lo",
                        columnNames = {"ma_chi_tiet", "ma_nguyen_lieu", "ma_lo"}
                )
        },
        indexes = {
                @Index(name = "idx_su_dung_lo_chi_tiet", columnList = "ma_chi_tiet"),
                @Index(name = "idx_su_dung_lo_nguyen_lieu", columnList = "ma_nguyen_lieu"),
                @Index(name = "idx_su_dung_lo_lo", columnList = "ma_lo"),
                @Index(name = "idx_su_dung_lo_thoi_gian", columnList = "thoi_gian_cap_phat")
        }
)
public class OrderItemBatchUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_su_dung")
    private Long maSuDung;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_chi_tiet", nullable = false)
    private OrderItem chiTietDonHang;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_nguyen_lieu", nullable = false)
    private Ingredient nguyenLieu;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_lo", nullable = false)
    private IngredientBatch loNguyenLieu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_giao_dich")
    private InventoryTransaction giaoDichKho;

    @Column(name = "so_luong_su_dung", precision = 15, scale = 3, nullable = false)
    private BigDecimal soLuongSuDung;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "DA_CAP_PHAT";

    @Column(name = "nguoi_cap_phat", length = 100, nullable = false)
    private String nguoiCapPhat;

    @Column(name = "thoi_gian_cap_phat", nullable = false)
    private LocalDateTime thoiGianCapPhat;

    @PrePersist
    void prePersist() {
        if (thoiGianCapPhat == null) {
            thoiGianCapPhat = LocalDateTime.now();
        }
        if (trangThai == null || trangThai.isBlank()) {
            trangThai = "DA_CAP_PHAT";
        }
        if (nguoiCapPhat == null || nguoiCapPhat.isBlank()) {
            nguoiCapPhat = "Hệ thống";
        }
    }
}
