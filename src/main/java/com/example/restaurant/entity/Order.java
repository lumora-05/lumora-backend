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
    @JoinColumn(name = "ma_ban")
    private DiningTable banAn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_nhan_vien")
    private Employee nhanVien;

    /** TAI_BAN hoặc GIAO_HANG. Dữ liệu cũ mặc định là TAI_BAN. */
    @Column(name = "loai_don", length = 20, nullable = false)
    private String loaiDon = "TAI_BAN";

    /** WEBSITE là nguồn mặc định; có thể mở rộng GRABFOOD ở giai đoạn sau. */
    @Column(name = "nguon_don", length = 30, nullable = false)
    private String nguonDon = "WEBSITE";

    @OneToOne(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private OrderDelivery giaoHang;

    /** Khuyến mãi đang áp dụng cho đơn. Mỗi đơn chỉ được dùng tối đa một mã. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_khuyen_mai")
    private Promotion khuyenMai;

    /** Khách hàng thân thiết gắn với đơn khi thu ngân xác nhận thanh toán. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_khach_hang")
    private Customer khachHang;

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

    /** Số điểm khách đã dùng cho đơn. */
    @Column(name = "diem_da_su_dung")
    private Integer diemDaSuDung = 0;

    /** Số tiền được giảm từ điểm tích lũy. */
    @Column(name = "tien_giam_tu_diem", precision = 12, scale = 2)
    private BigDecimal tienGiamTuDiem = BigDecimal.ZERO;

    /** Số điểm khách nhận được sau khi thanh toán. */
    @Column(name = "diem_duoc_cong")
    private Integer diemDuocCong = 0;

    /** Số tiền cuối cùng phải thanh toán sau khuyến mãi và điểm. */
    @Column(name = "tong_tien", precision = 12, scale = 2, nullable = false)
    private BigDecimal tongTien = BigDecimal.ZERO;

    @OneToMany(mappedBy = "donHang", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("maChiTiet ASC")
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

    public void setGiaoHang(OrderDelivery giaoHang) {
        this.giaoHang = giaoHang;
        if (giaoHang != null) {
            giaoHang.setDonHang(this);
        }
    }

    @Transient
    public boolean isDeliveryOrder() {
        return "GIAO_HANG".equalsIgnoreCase(loaiDon);
    }

    /** Trường tiện dụng cho frontend, bên cạnh object khuyenMai đầy đủ. */
    @Transient
    public String getMaCodeKhuyenMai() {
        return khuyenMai == null ? null : khuyenMai.getMaCode();
    }

    private void initializeMoneyDefaults() {
        if (loaiDon == null || loaiDon.isBlank()) {
            loaiDon = "TAI_BAN";
        }
        if (nguonDon == null || nguonDon.isBlank()) {
            nguonDon = "WEBSITE";
        }
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
        if (diemDaSuDung == null || diemDaSuDung < 0) {
            diemDaSuDung = 0;
        }
        if (tienGiamTuDiem == null || tienGiamTuDiem.signum() < 0) {
            tienGiamTuDiem = BigDecimal.ZERO;
        }
        if (diemDuocCong == null || diemDuocCong < 0) {
            diemDuocCong = 0;
        }
        tamTinh = tamTinh.setScale(2, RoundingMode.HALF_UP);
        tienGiam = tienGiam.setScale(2, RoundingMode.HALF_UP);
        tienGiamTuDiem = tienGiamTuDiem.setScale(2, RoundingMode.HALF_UP);
        tongTien = tongTien.setScale(2, RoundingMode.HALF_UP);
    }
}
