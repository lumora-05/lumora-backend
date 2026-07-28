package com.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "nhan_vien")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nhan_vien")
    private Integer maNhanVien;

    @Column(name = "ho_ten", length = 100, nullable = false)
    private String hoTen;

    @Column(name = "so_dien_thoai", length = 15)
    private String soDienThoai;

    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "anh_dai_dien", length = 500)
    private String anhDaiDien;

    @Column(name = "ten_dang_nhap", length = 50, nullable = false, unique = true)
    private String tenDangNhap;

    @JsonIgnore
    @Column(name = "mat_khau", length = 255, nullable = false)
    private String matKhau;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_vai_tro", nullable = false)
    private Role vaiTro;

    /**
     * Tên khu vực mà nhân viên phục vụ được phân công, khớp với ban_an.khu_vuc.
     * Chỉ áp dụng cho vai trò WAITER; các vai trò khác luôn để trống.
     */
    @Column(name = "khu_vuc_phu_trach", length = 100)
    private String khuVucPhuTrach;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "DANG_LAM_VIEC";

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();
}
