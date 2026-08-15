package com.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "khach_hang",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_khach_hang_so_dien_thoai",
                columnNames = "so_dien_thoai"
        )
)
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_khach_hang")
    private Integer maKhachHang;

    @Column(name = "ho_ten", length = 100, nullable = false)
    private String hoTen;

    @Column(name = "so_dien_thoai", length = 15, nullable = false)
    private String soDienThoai;

    /** Null với khách vãng lai/khách thân thiết chưa tạo tài khoản. */
    @JsonIgnore
    @Column(name = "mat_khau_hash", length = 100)
    private String matKhauHash;

    @Column(name = "diem_tich_luy", nullable = false)
    private Integer diemTichLuy = 0;

    @Column(name = "tong_chi_tieu", precision = 14, scale = 2, nullable = false)
    private BigDecimal tongChiTieu = BigDecimal.ZERO;

    @Column(name = "trang_thai", length = 20, nullable = false)
    private String trangThai = "HOAT_DONG";

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "thoi_gian_cap_nhat", nullable = false)
    private LocalDateTime thoiGianCapNhat = LocalDateTime.now();

    @JsonIgnore
    @Version
    @Column(name = "phien_ban", nullable = false)
    private Long phienBan = 0L;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (thoiGianTao == null) {
            thoiGianTao = now;
        }
        thoiGianCapNhat = now;
        initializeDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        thoiGianCapNhat = LocalDateTime.now();
        initializeDefaults();
    }

    @PostLoad
    public void postLoad() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        if (diemTichLuy == null || diemTichLuy < 0) {
            diemTichLuy = 0;
        }
        if (tongChiTieu == null || tongChiTieu.signum() < 0) {
            tongChiTieu = BigDecimal.ZERO;
        }
        tongChiTieu = tongChiTieu.setScale(2, RoundingMode.HALF_UP);
        if (trangThai == null || trangThai.isBlank()) {
            trangThai = "HOAT_DONG";
        }
    }
}
