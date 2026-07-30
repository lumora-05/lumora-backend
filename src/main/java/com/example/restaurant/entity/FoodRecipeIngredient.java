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
        name = "cong_thuc_mon_an",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cong_thuc_mon_nguyen_lieu",
                        columnNames = {"ma_mon_an", "ma_nguyen_lieu"}
                )
        },
        indexes = {
                @Index(name = "idx_cong_thuc_mon_an_mon", columnList = "ma_mon_an"),
                @Index(name = "idx_cong_thuc_mon_an_nguyen_lieu", columnList = "ma_nguyen_lieu")
        }
)
public class FoodRecipeIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_cong_thuc")
    private Long maCongThuc;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_mon_an", nullable = false)
    private Food monAn;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_nguyen_lieu", nullable = false)
    private Ingredient nguyenLieu;

    /** Định lượng nguyên liệu cho một phần món, dùng cùng đơn vị với nguyên liệu. */
    @Column(name = "dinh_luong", precision = 15, scale = 3, nullable = false)
    private BigDecimal dinhLuong;

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
        if (trangThai == null) {
            trangThai = true;
        }
    }

    @PreUpdate
    void preUpdate() {
        ngayCapNhat = LocalDateTime.now();
        if (trangThai == null) {
            trangThai = true;
        }
    }
}
