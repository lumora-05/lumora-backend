package com.example.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cai_dat_he_thong")
public class SystemSetting {
    public static final Integer SINGLETON_ID = 1;

    @Id
    @Column(name = "ma_cai_dat")
    private Integer maCaiDat = SINGLETON_ID;

    @Column(name = "ten_nha_hang", nullable = false, length = 120)
    private String tenNhaHang;

    @Column(name = "dia_chi", length = 255)
    private String diaChi;

    @Column(name = "so_dien_thoai", length = 30)
    private String soDienThoai;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "gio_mo_cua", length = 100)
    private String gioMoCua;

    @Column(name = "reservation_url", length = 255)
    private String reservationUrl;

    @Column(name = "menu_url", length = 255)
    private String menuUrl;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "banner_url", length = 1000)
    private String bannerUrl;

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao;

    @Column(name = "ngay_cap_nhat", nullable = false)
    private LocalDateTime ngayCapNhat;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (maCaiDat == null) {
            maCaiDat = SINGLETON_ID;
        }
        if (ngayTao == null) {
            ngayTao = now;
        }
        ngayCapNhat = now;
    }

    @PreUpdate
    void onUpdate() {
        ngayCapNhat = LocalDateTime.now();
    }
}
