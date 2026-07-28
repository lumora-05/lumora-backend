package com.example.restaurant.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "hoat_dong_he_thong",
        indexes = @Index(name = "idx_hoat_dong_thoi_gian", columnList = "thoi_gian")
)
public class SystemActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_hoat_dong")
    private Long maHoatDong;

    @Column(name = "loai_hoat_dong", length = 50, nullable = false)
    private String loaiHoatDong;

    @Column(name = "noi_dung", length = 500, nullable = false)
    private String noiDung;

    @Column(name = "doi_tuong_id")
    private Integer doiTuongId;

    @Column(name = "nguoi_thuc_hien", length = 100)
    private String nguoiThucHien;

    @Column(name = "thoi_gian", nullable = false)
    private LocalDateTime thoiGian = LocalDateTime.now();

    public SystemActivity() {
    }

    @PrePersist
    void prePersist() {
        if (thoiGian == null) {
            thoiGian = LocalDateTime.now();
        }
    }

    public Long getMaHoatDong() {
        return maHoatDong;
    }

    public void setMaHoatDong(Long maHoatDong) {
        this.maHoatDong = maHoatDong;
    }

    public String getLoaiHoatDong() {
        return loaiHoatDong;
    }

    public void setLoaiHoatDong(String loaiHoatDong) {
        this.loaiHoatDong = loaiHoatDong;
    }

    public String getNoiDung() {
        return noiDung;
    }

    public void setNoiDung(String noiDung) {
        this.noiDung = noiDung;
    }

    public Integer getDoiTuongId() {
        return doiTuongId;
    }

    public void setDoiTuongId(Integer doiTuongId) {
        this.doiTuongId = doiTuongId;
    }

    public String getNguoiThucHien() {
        return nguoiThucHien;
    }

    public void setNguoiThucHien(String nguoiThucHien) {
        this.nguoiThucHien = nguoiThucHien;
    }

    public LocalDateTime getThoiGian() {
        return thoiGian;
    }

    public void setThoiGian(LocalDateTime thoiGian) {
        this.thoiGian = thoiGian;
    }
}
