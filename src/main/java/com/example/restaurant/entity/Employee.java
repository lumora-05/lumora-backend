package com.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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
     * Khu vực phụ trách cũ, được giữ lại để tương thích với dữ liệu/frontend cũ.
     * Khi dùng cơ chế nhiều khu vực, trường này lưu khu vực đầu tiên để client cũ
     * vẫn hiển thị được một giá trị hợp lệ.
     */
    @Column(name = "khu_vuc_phu_trach", length = 100)
    private String khuVucPhuTrach;

    /**
     * Danh sách khu vực mà nhân viên phục vụ được phân công.
     * Một WAITER có thể phụ trách một hoặc nhiều khu vực; vai trò khác luôn để trống.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "nhan_vien_khu_vuc",
            joinColumns = @JoinColumn(name = "ma_nhan_vien")
    )
    @Column(name = "khu_vuc", length = 100, nullable = false)
    private Set<String> danhSachKhuVucPhuTrach = new LinkedHashSet<>();

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "DANG_LAM_VIEC";

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();
}
