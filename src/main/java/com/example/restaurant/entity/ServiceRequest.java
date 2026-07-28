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
        name = "yeu_cau_phuc_vu",
        indexes = {
                @Index(name = "idx_ycpv_ban_trang_thai", columnList = "ma_ban,trang_thai"),
                @Index(name = "idx_ycpv_khu_vuc_trang_thai", columnList = "khu_vuc,trang_thai"),
                @Index(name = "idx_ycpv_thoi_gian_tao", columnList = "thoi_gian_tao")
        }
)
public class ServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_yeu_cau")
    private Integer maYeuCau;

    /**
     * Lưu mã và thông tin bàn dạng snapshot thay vì quan hệ khóa ngoại để lịch sử
     * yêu cầu không làm ảnh hưởng chức năng xóa bàn của hệ thống hiện tại.
     */
    @Column(name = "ma_ban", nullable = false)
    private Integer maBan;

    @Column(name = "ten_ban", length = 50, nullable = false)
    private String tenBan;

    @Column(name = "khu_vuc", length = 100, nullable = false)
    private String khuVuc;

    @Column(name = "loai_yeu_cau", length = 50, nullable = false)
    private String loaiYeuCau;

    @Column(name = "noi_dung", length = 500)
    private String noiDung;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "MOI";

    @Column(name = "muc_do_uu_tien", length = 30, nullable = false)
    private String mucDoUuTien = "BINH_THUONG";

    @Column(name = "ma_nhan_vien_tiep_nhan")
    private Integer maNhanVienTiepNhan;

    @Column(name = "ten_nhan_vien_tiep_nhan", length = 100)
    private String tenNhanVienTiepNhan;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "thoi_gian_tiep_nhan")
    private LocalDateTime thoiGianTiepNhan;

    @Column(name = "thoi_gian_hoan_thanh")
    private LocalDateTime thoiGianHoanThanh;

    @Column(name = "thoi_gian_huy")
    private LocalDateTime thoiGianHuy;

    @Column(name = "ma_nguoi_huy")
    private Integer maNguoiHuy;

    @Column(name = "ten_nguoi_huy", length = 100)
    private String tenNguoiHuy;

    @Column(name = "nguon_huy", length = 30)
    private String nguonHuy;

    @Column(name = "ly_do_huy", length = 500)
    private String lyDoHuy;

    /** Tăng thêm một lớp bảo vệ khi hai người cùng thao tác trên một yêu cầu. */
    @Version
    @Column(name = "phien_ban", nullable = false)
    private Long phienBan = 0L;

    @PrePersist
    void prePersist() {
        if (thoiGianTao == null) {
            thoiGianTao = LocalDateTime.now();
        }
        if (trangThai == null || trangThai.isBlank()) {
            trangThai = "MOI";
        }
        if (mucDoUuTien == null || mucDoUuTien.isBlank()) {
            mucDoUuTien = "BINH_THUONG";
        }
        if (khuVuc == null || khuVuc.isBlank()) {
            khuVuc = "Khu vực chung";
        }
    }
}
