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
@Table(name = "chi_tiet_don_hang")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_chi_tiet")
    private Integer maChiTiet;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_don_hang", nullable = false)
    private Order donHang;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_mon_an", nullable = false)
    private Food monAn;

    @Column(name = "so_luong", nullable = false)
    private Integer soLuong;

    @Column(name = "don_gia", precision = 12, scale = 2, nullable = false)
    private BigDecimal donGia;

    @Column(name = "ghi_chu", length = 255)
    private String ghiChu;

    @Column(name = "trang_thai_mon", length = 30, nullable = false)
    private String trangThaiMon = "CHO_BEP";

    /**
     * Lần gọi món trong cùng một đơn. Lần đầu = 1, gọi thêm = 2, 3...
     * Trường này giúp bếp nhận biết món nào vừa được khách gọi thêm.
     */
    @Column(name = "lan_goi")
    private Integer lanGoi;

    @Column(name = "thoi_gian_them")
    private LocalDateTime thoiGianThem;

    /**
     * Trạng thái xử lý yêu cầu hủy: CHO_DUYET, DA_DUYET hoặc TU_CHOI.
     * Trạng thái món chỉ chuyển sang DA_HUY sau khi yêu cầu được chấp nhận.
     */
    @Column(name = "trang_thai_huy", length = 30)
    private String trangThaiHuy;

    @Column(name = "trang_thai_truoc_huy", length = 30)
    private String trangThaiTruocHuy;

    @Column(name = "ma_ly_do_huy", length = 50)
    private String maLyDoHuy;

    @Column(name = "ly_do_huy", length = 255)
    private String lyDoHuy;

    @Column(name = "ghi_chu_huy", length = 255)
    private String ghiChuHuy;

    /** KHACH_HANG, NHAN_VIEN_PHUC_VU hoặc ADMIN. */
    @Column(name = "nguon_yeu_cau_huy", length = 30)
    private String nguonYeuCauHuy;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nguoi_yeu_cau_huy")
    private Employee nguoiYeuCauHuy;

    @Column(name = "thoi_gian_yeu_cau_huy")
    private LocalDateTime thoiGianYeuCauHuy;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nguoi_xu_ly_huy")
    private Employee nguoiXuLyHuy;

    @Column(name = "thoi_gian_xu_ly_huy")
    private LocalDateTime thoiGianXuLyHuy;

    @Column(name = "ghi_chu_xu_ly_huy", length = 255)
    private String ghiChuXuLyHuy;
}
