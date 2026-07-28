package com.example.restaurant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dat_ban", indexes = {
        @Index(name = "idx_dat_ban_ngay_gio", columnList = "ngay_gio_den"),
        @Index(name = "idx_dat_ban_trang_thai", columnList = "trang_thai"),
        @Index(name = "idx_dat_ban_so_dien_thoai", columnList = "so_dien_thoai")
})
public class TableReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_dat_ban")
    private Integer maDatBan;

    /** Mã công khai để khách tra cứu, không dùng id tăng dần có thể đoán. */
    @Column(name = "ma_tra_cuu", length = 24, nullable = false, unique = true)
    private String maTraCuu;

    @Column(name = "ho_ten_khach", length = 100, nullable = false)
    private String hoTenKhach;

    @Column(name = "so_dien_thoai", length = 20, nullable = false)
    private String soDienThoai;

    @Column(name = "ngay_gio_den", nullable = false)
    private LocalDateTime ngayGioDen;

    @Column(name = "thoi_gian_ket_thuc_du_kien", nullable = false)
    private LocalDateTime thoiGianKetThucDuKien;

    @Column(name = "thoi_luong_phut", nullable = false)
    private Integer thoiLuongPhut = 120;

    @Column(name = "so_luong_khach", nullable = false)
    private Integer soLuongKhach;

    @Column(name = "khu_vuc_mong_muon", length = 100)
    private String khuVucMongMuon;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_ban_du_kien")
    private DiningTable banDuKien;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_ban_thuc_te")
    private DiningTable banThucTe;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_don_hang", unique = true)
    private Order donHang;

    @Column(name = "ghi_chu", length = 500)
    private String ghiChu;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "CHO_XAC_NHAN";

    @Column(name = "ly_do_huy_tu_choi", length = 500)
    private String lyDoHuyTuChoi;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "thoi_gian_cap_nhat", nullable = false)
    private LocalDateTime thoiGianCapNhat = LocalDateTime.now();

    @Column(name = "thoi_gian_xac_nhan")
    private LocalDateTime thoiGianXacNhan;

    @Column(name = "thoi_gian_check_in")
    private LocalDateTime thoiGianCheckIn;

    @Column(name = "thoi_gian_xep_ban")
    private LocalDateTime thoiGianXepBan;

    @Column(name = "thoi_gian_hoan_thanh")
    private LocalDateTime thoiGianHoanThanh;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nguoi_xac_nhan")
    private Employee nguoiXacNhan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nguoi_check_in")
    private Employee nguoiCheckIn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nguoi_xep_ban")
    private Employee nguoiXepBan;


    public TableReservation() {
    }

    public Integer getMaDatBan() { return maDatBan; }
    public void setMaDatBan(Integer maDatBan) { this.maDatBan = maDatBan; }
    public String getMaTraCuu() { return maTraCuu; }
    public void setMaTraCuu(String maTraCuu) { this.maTraCuu = maTraCuu; }
    public String getHoTenKhach() { return hoTenKhach; }
    public void setHoTenKhach(String hoTenKhach) { this.hoTenKhach = hoTenKhach; }
    public String getSoDienThoai() { return soDienThoai; }
    public void setSoDienThoai(String soDienThoai) { this.soDienThoai = soDienThoai; }
    public LocalDateTime getNgayGioDen() { return ngayGioDen; }
    public void setNgayGioDen(LocalDateTime ngayGioDen) { this.ngayGioDen = ngayGioDen; }
    public LocalDateTime getThoiGianKetThucDuKien() { return thoiGianKetThucDuKien; }
    public void setThoiGianKetThucDuKien(LocalDateTime value) { this.thoiGianKetThucDuKien = value; }
    public Integer getThoiLuongPhut() { return thoiLuongPhut; }
    public void setThoiLuongPhut(Integer thoiLuongPhut) { this.thoiLuongPhut = thoiLuongPhut; }
    public Integer getSoLuongKhach() { return soLuongKhach; }
    public void setSoLuongKhach(Integer soLuongKhach) { this.soLuongKhach = soLuongKhach; }
    public String getKhuVucMongMuon() { return khuVucMongMuon; }
    public void setKhuVucMongMuon(String value) { this.khuVucMongMuon = value; }
    public DiningTable getBanDuKien() { return banDuKien; }
    public void setBanDuKien(DiningTable banDuKien) { this.banDuKien = banDuKien; }
    public DiningTable getBanThucTe() { return banThucTe; }
    public void setBanThucTe(DiningTable banThucTe) { this.banThucTe = banThucTe; }
    public Order getDonHang() { return donHang; }
    public void setDonHang(Order donHang) { this.donHang = donHang; }
    public String getGhiChu() { return ghiChu; }
    public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
    public String getLyDoHuyTuChoi() { return lyDoHuyTuChoi; }
    public void setLyDoHuyTuChoi(String value) { this.lyDoHuyTuChoi = value; }
    public LocalDateTime getThoiGianTao() { return thoiGianTao; }
    public void setThoiGianTao(LocalDateTime value) { this.thoiGianTao = value; }
    public LocalDateTime getThoiGianCapNhat() { return thoiGianCapNhat; }
    public void setThoiGianCapNhat(LocalDateTime value) { this.thoiGianCapNhat = value; }
    public LocalDateTime getThoiGianXacNhan() { return thoiGianXacNhan; }
    public void setThoiGianXacNhan(LocalDateTime value) { this.thoiGianXacNhan = value; }
    public LocalDateTime getThoiGianCheckIn() { return thoiGianCheckIn; }
    public void setThoiGianCheckIn(LocalDateTime value) { this.thoiGianCheckIn = value; }
    public LocalDateTime getThoiGianXepBan() { return thoiGianXepBan; }
    public void setThoiGianXepBan(LocalDateTime value) { this.thoiGianXepBan = value; }
    public LocalDateTime getThoiGianHoanThanh() { return thoiGianHoanThanh; }
    public void setThoiGianHoanThanh(LocalDateTime value) { this.thoiGianHoanThanh = value; }
    public Employee getNguoiXacNhan() { return nguoiXacNhan; }
    public void setNguoiXacNhan(Employee value) { this.nguoiXacNhan = value; }
    public Employee getNguoiCheckIn() { return nguoiCheckIn; }
    public void setNguoiCheckIn(Employee value) { this.nguoiCheckIn = value; }
    public Employee getNguoiXepBan() { return nguoiXepBan; }
    public void setNguoiXepBan(Employee value) { this.nguoiXepBan = value; }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (thoiGianTao == null) {
            thoiGianTao = now;
        }
        thoiGianCapNhat = now;
        if (trangThai == null || trangThai.isBlank()) {
            trangThai = "CHO_XAC_NHAN";
        }
        if (thoiLuongPhut == null || thoiLuongPhut < 30) {
            thoiLuongPhut = 120;
        }
        if (ngayGioDen != null && thoiGianKetThucDuKien == null) {
            thoiGianKetThucDuKien = ngayGioDen.plusMinutes(thoiLuongPhut);
        }
    }

    @PreUpdate
    void preUpdate() {
        thoiGianCapNhat = LocalDateTime.now();
        if (ngayGioDen != null && thoiLuongPhut != null) {
            thoiGianKetThucDuKien = ngayGioDen.plusMinutes(thoiLuongPhut);
        }
    }
}
