package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "don_hang")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_don_hang")
    private Integer maDonHang;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_ban", nullable = false)
    private DiningTable banAn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nhan_vien")
    private Employee nhanVien;

    /** Khuyến mãi đang áp dụng cho đơn. Mỗi đơn chỉ được dùng tối đa một mã. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_khuyen_mai")
    private Promotion khuyenMai;

    @Column(name = "thoi_gian_dat", nullable = false)
    private LocalDateTime thoiGianDat = LocalDateTime.now();

    @Column(name = "thoi_gian_cap_nhat")
    private LocalDateTime thoiGianCapNhat = LocalDateTime.now();

    @Column(name = "thoi_gian_san_sang")
    private LocalDateTime thoiGianSanSang;

    @Column(name = "thoi_gian_yeu_cau_thanh_toan")
    private LocalDateTime thoiGianYeuCauThanhToan;

    @Column(name = "thoi_gian_ap_dung_khuyen_mai")
    private LocalDateTime thoiGianApDungKhuyenMai;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "CHO_XAC_NHAN";

    @Column(name = "ghi_chu", length = 255)
    private String ghiChu;

    /** Tổng tiền món trước khuyến mãi. */
    @Column(name = "tam_tinh", precision = 12, scale = 2)
    private BigDecimal tamTinh = BigDecimal.ZERO;

    /** Số tiền được giảm. */
    @Column(name = "tien_giam", precision = 12, scale = 2)
    private BigDecimal tienGiam = BigDecimal.ZERO;

    /** Số tiền cuối cùng phải thanh toán. */
    @Column(name = "tong_tien", precision = 12, scale = 2, nullable = false)
    private BigDecimal tongTien = BigDecimal.ZERO;

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> chiTietDonHang = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (thoiGianDat == null) {
            thoiGianDat = now;
        }
        thoiGianCapNhat = now;
        initializeMoneyDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        thoiGianCapNhat = LocalDateTime.now();
        initializeMoneyDefaults();
    }

    @PostLoad
    public void postLoad() {
        initializeMoneyDefaults();
    }

    public void addItem(OrderItem item) {
        chiTietDonHang.add(item);
        item.setDonHang(this);
    }

    /** Trường tiện dụng cho frontend, bên cạnh object khuyenMai đầy đủ. */
    @Transient
    public String getMaCodeKhuyenMai() {
        return khuyenMai == null ? null : khuyenMai.getMaCode();
    }

    private void initializeMoneyDefaults() {
        if (tongTien == null) {
            tongTien = BigDecimal.ZERO;
        }
        if (tamTinh == null) {
            // Dữ liệu cũ chưa có cột tạm tính: coi tổng tiền cũ là tạm tính.
            tamTinh = tongTien;
        }
        if (tienGiam == null) {
            tienGiam = BigDecimal.ZERO;
        }
        tamTinh = tamTinh.setScale(2, RoundingMode.HALF_UP);
        tienGiam = tienGiam.setScale(2, RoundingMode.HALF_UP);
        tongTien = tongTien.setScale(2, RoundingMode.HALF_UP);
    }
}
