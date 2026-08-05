package com.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "chi_tiet_dat_mon_truoc", indexes = {
        @Index(name = "idx_dat_mon_truoc_dat_ban", columnList = "ma_dat_ban"),
        @Index(name = "idx_dat_mon_truoc_mon_an", columnList = "ma_mon_an")
})
public class ReservationPreorderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_chi_tiet_dat_mon_truoc")
    private Integer maChiTietDatMonTruoc;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_dat_ban", nullable = false)
    private TableReservation datBan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_mon_an", nullable = false)
    private Food monAn;

    @Column(name = "so_luong", nullable = false)
    private Integer soLuong;

    /** Lưu giá tại thời điểm khách chọn món để tránh thay đổi giá làm sai tổng dự kiến. */
    @Column(name = "don_gia", precision = 12, scale = 2, nullable = false)
    private BigDecimal donGia;

    @Column(name = "ghi_chu", length = 255)
    private String ghiChu;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "thoi_gian_cap_nhat", nullable = false)
    private LocalDateTime thoiGianCapNhat = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (thoiGianTao == null) {
            thoiGianTao = now;
        }
        thoiGianCapNhat = now;
    }

    @PreUpdate
    void preUpdate() {
        thoiGianCapNhat = LocalDateTime.now();
    }
}
