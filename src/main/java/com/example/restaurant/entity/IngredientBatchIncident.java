package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "su_co_lo_nguyen_lieu",
        indexes = {
                @Index(name = "idx_su_co_lo_nguyen_lieu_lo", columnList = "ma_lo"),
                @Index(name = "idx_su_co_lo_nguyen_lieu_trang_thai", columnList = "trang_thai"),
                @Index(name = "idx_su_co_lo_nguyen_lieu_thoi_gian", columnList = "thoi_gian_phat_hien")
        }
)
public class IngredientBatchIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_su_co")
    private Long maSuCo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ma_lo", nullable = false)
    private IngredientBatch loNguyenLieu;

    @Column(name = "loai_su_co", length = 50, nullable = false)
    private String loaiSuCo;

    @Column(name = "muc_do", length = 30, nullable = false)
    private String mucDo;

    @Column(name = "ly_do", length = 1000, nullable = false)
    private String lyDo;

    @Column(name = "ghi_chu", length = 1000)
    private String ghiChu;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "MOI";

    @Column(name = "nguoi_phat_hien", length = 100, nullable = false)
    private String nguoiPhatHien;

    @Column(name = "thoi_gian_phat_hien", nullable = false)
    private LocalDateTime thoiGianPhatHien;

    @Column(name = "nguoi_xu_ly", length = 100)
    private String nguoiXuLy;

    @Column(name = "thoi_gian_xu_ly")
    private LocalDateTime thoiGianXuLy;

    @Column(name = "ket_qua_xu_ly", length = 1000)
    private String ketQuaXuLy;

    @PrePersist
    void prePersist() {
        if (thoiGianPhatHien == null) {
            thoiGianPhatHien = LocalDateTime.now();
        }
        if (trangThai == null || trangThai.isBlank()) {
            trangThai = "MOI";
        }
        if (nguoiPhatHien == null || nguoiPhatHien.isBlank()) {
            nguoiPhatHien = "Hệ thống";
        }
    }
}
