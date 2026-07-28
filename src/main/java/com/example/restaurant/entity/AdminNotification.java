package com.example.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
        name = "thong_bao_admin",
        indexes = {
                @Index(name = "idx_thong_bao_admin_da_doc", columnList = "da_doc"),
                @Index(name = "idx_thong_bao_admin_thoi_gian", columnList = "thoi_gian_tao"),
                @Index(name = "idx_thong_bao_admin_nguyen_lieu", columnList = "ma_nguyen_lieu")
        }
)
public class AdminNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_thong_bao")
    private Long maThongBao;

    @Column(name = "loai_thong_bao", length = 50, nullable = false)
    private String loaiThongBao;

    @Column(name = "tieu_de", length = 200, nullable = false)
    private String tieuDe;

    @Column(name = "noi_dung", length = 500, nullable = false)
    private String noiDung;

    @Column(name = "ma_nguyen_lieu")
    private Integer maNguyenLieu;

    @Column(name = "ten_nguyen_lieu", length = 150)
    private String tenNguyenLieu;

    @Column(name = "so_luong_ton", precision = 15, scale = 3)
    private BigDecimal soLuongTon;

    @Column(name = "muc_ton_toi_thieu", precision = 15, scale = 3)
    private BigDecimal mucTonToiThieu;

    @Column(name = "don_vi_tinh", length = 30)
    private String donViTinh;

    @Column(name = "trang_thai_ton_kho", length = 30)
    private String trangThaiTonKho;

    @Column(name = "da_doc", nullable = false)
    private Boolean daDoc = false;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao;

    @Column(name = "thoi_gian_doc")
    private LocalDateTime thoiGianDoc;

    @PrePersist
    void prePersist() {
        if (daDoc == null) {
            daDoc = false;
        }
        if (thoiGianTao == null) {
            thoiGianTao = LocalDateTime.now();
        }
    }
}
