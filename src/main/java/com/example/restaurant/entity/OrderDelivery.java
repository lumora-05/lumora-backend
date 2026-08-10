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
@Table(
        name = "giao_hang_don_hang",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_giao_hang_don_hang", columnNames = "ma_don_hang"),
                @UniqueConstraint(name = "uk_giao_hang_tracking_token", columnNames = "tracking_token"),
                @UniqueConstraint(name = "uk_giao_hang_ma_van_chuyen", columnNames = "ma_van_chuyen"),
                @UniqueConstraint(name = "uk_giao_hang_client_request", columnNames = "client_request_id"),
                @UniqueConstraint(name = "uk_giao_hang_ma_giao_dich", columnNames = "ma_giao_dich")
        }
)
public class OrderDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_giao_hang")
    private Long maGiaoHang;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_don_hang", nullable = false, unique = true)
    private Order donHang;

    /** Token ngẫu nhiên để khách tra cứu, không dùng mã đơn tăng dần. */
    @Column(name = "tracking_token", length = 80, nullable = false, unique = true)
    private String trackingToken;

    /** Khóa chống tạo trùng khi khách bấm đặt hàng nhiều lần. */
    @Column(name = "client_request_id", length = 100, unique = true)
    private String clientRequestId;

    /** Mã vận đơn do dịch vụ vận chuyển bên ngoài (hoặc bản mô phỏng) trả về. */
    @Column(name = "ma_van_chuyen", length = 50, unique = true)
    private String maVanChuyen;

    @Column(name = "ten_nguoi_nhan", length = 120, nullable = false)
    private String tenNguoiNhan;

    @Column(name = "so_dien_thoai_nhan", length = 20, nullable = false)
    private String soDienThoaiNhan;

    @Column(name = "dia_chi_giao_hang", length = 500, nullable = false)
    private String diaChiGiaoHang;

    @Column(name = "dia_chi_chi_tiet", length = 500)
    private String diaChiChiTiet;

    @Column(name = "phuong_xa", length = 120)
    private String phuongXa;

    @Column(name = "quan_huyen", length = 100)
    private String quanHuyen;

    @Column(name = "tinh_thanh", length = 100)
    private String tinhThanh;

    @Column(name = "khu_vuc_giao_hang", length = 30, nullable = false)
    private String khuVucGiaoHang;

    @Column(name = "google_maps", nullable = false)
    private Boolean googleMaps = false;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "quang_duong_met")
    private Integer quangDuongMet;

    @Column(name = "thoi_gian_du_kien_giay")
    private Long thoiGianDuKienGiay;

    @Column(name = "google_route_polyline", columnDefinition = "TEXT")
    private String googleRoutePolyline;

    @Column(name = "ghi_chu_giao_hang", length = 500)
    private String ghiChuGiaoHang;

    @Column(name = "phi_giao_hang", precision = 12, scale = 2, nullable = false)
    private BigDecimal phiGiaoHang = BigDecimal.ZERO;

    /** COD hoặc VIETQR. */
    @Column(name = "phuong_thuc_thanh_toan", length = 20, nullable = false)
    private String phuongThucThanhToan;

    /** CHO_THANH_TOAN, DA_THANH_TOAN, CHO_HOAN_TIEN, DA_HOAN_TIEN hoặc HET_HAN. */
    @Column(name = "trang_thai_thanh_toan", length = 30, nullable = false)
    private String trangThaiThanhToan = "CHO_THANH_TOAN";

    @Column(name = "ma_giao_dich", length = 100, unique = true)
    private String maGiaoDich;

    @Column(name = "so_tien_da_thanh_toan", precision = 12, scale = 2)
    private BigDecimal soTienDaThanhToan = BigDecimal.ZERO;

    @Column(name = "ghi_chu_thanh_toan", length = 500)
    private String ghiChuThanhToan;

    @Column(name = "so_tien_can_hoan", precision = 12, scale = 2)
    private BigDecimal soTienCanHoan = BigDecimal.ZERO;

    @Column(name = "so_tien_da_hoan", precision = 12, scale = 2)
    private BigDecimal soTienDaHoan = BigDecimal.ZERO;

    @Column(name = "thoi_gian_het_han_thanh_toan")
    private LocalDateTime thoiGianHetHanThanhToan;

    @Column(name = "da_canh_bao_cho_xac_nhan", nullable = false)
    private Boolean daCanhBaoChoXacNhan = false;

    /** CHO_THANH_TOAN, DANG_CHUAN_BI, CHO_TAI_XE_NHAN, DANG_GIAO, CHO_DOI_SOAT (nội bộ), HOAN_THANH, GIAO_THAT_BAI, DA_HUY. */
    @Column(name = "trang_thai_giao_hang", length = 30, nullable = false)
    private String trangThaiGiaoHang = "DANG_CHUAN_BI";

    @Column(name = "don_vi_van_chuyen", length = 120)
    private String donViVanChuyen;

    @Column(name = "ten_nguoi_giao", length = 120)
    private String tenNguoiGiao;

    @Column(name = "so_dien_thoai_nguoi_giao", length = 20)
    private String soDienThoaiNguoiGiao;

    @Column(name = "ghi_chu_ban_giao", length = 500)
    private String ghiChuBanGiao;

    @Column(name = "ly_do_tu_choi", length = 500)
    private String lyDoTuChoi;

    @Column(name = "ly_do_giao_that_bai", length = 500)
    private String lyDoGiaoThatBai;

    /** Trạng thái do đối tác giao hàng gửi về qua webhook: GIAO_THANH_CONG/GIAO_THAT_BAI. */
    @Column(name = "trang_thai_doi_tac", length = 40)
    private String trangThaiDoiTac;

    @Column(name = "ly_do_doi_tac", length = 500)
    private String lyDoDoiTac;

    @Column(name = "nguon_cap_nhat_doi_tac", length = 40)
    private String nguonCapNhatDoiTac;

    @Column(name = "ma_su_kien_doi_tac", length = 120)
    private String maSuKienDoiTac;

    @Column(name = "thoi_gian_cap_nhat_doi_tac")
    private LocalDateTime thoiGianCapNhatDoiTac;

    @Column(name = "thoi_gian_xac_nhan")
    private LocalDateTime thoiGianXacNhan;

    @Column(name = "thoi_gian_san_sang")
    private LocalDateTime thoiGianSanSang;

    @Column(name = "thoi_gian_ban_giao")
    private LocalDateTime thoiGianBanGiao;

    @Column(name = "thoi_gian_giao_thanh_cong")
    private LocalDateTime thoiGianGiaoThanhCong;

    @Column(name = "thoi_gian_huy")
    private LocalDateTime thoiGianHuy;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime thoiGianTao = LocalDateTime.now();

    @Column(name = "thoi_gian_cap_nhat")
    private LocalDateTime thoiGianCapNhat = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (thoiGianTao == null) {
            thoiGianTao = now;
        }
        thoiGianCapNhat = now;
        if (phiGiaoHang == null) {
            phiGiaoHang = BigDecimal.ZERO;
        }
        if (soTienDaThanhToan == null || soTienDaThanhToan.signum() < 0) {
            soTienDaThanhToan = BigDecimal.ZERO;
        }
        if (soTienCanHoan == null || soTienCanHoan.signum() < 0) {
            soTienCanHoan = BigDecimal.ZERO;
        }
        if (soTienDaHoan == null || soTienDaHoan.signum() < 0) {
            soTienDaHoan = BigDecimal.ZERO;
        }
        if (daCanhBaoChoXacNhan == null) {
            daCanhBaoChoXacNhan = false;
        }
        if (googleMaps == null) {
            googleMaps = false;
        }
        if (trangThaiThanhToan == null || trangThaiThanhToan.isBlank()) {
            trangThaiThanhToan = "CHO_THANH_TOAN";
        }
        if (trangThaiGiaoHang == null || trangThaiGiaoHang.isBlank()) {
            trangThaiGiaoHang = "DANG_CHUAN_BI";
        }
    }

    @PreUpdate
    void preUpdate() {
        thoiGianCapNhat = LocalDateTime.now();
        if (phiGiaoHang == null) {
            phiGiaoHang = BigDecimal.ZERO;
        }
        if (soTienDaThanhToan == null || soTienDaThanhToan.signum() < 0) {
            soTienDaThanhToan = BigDecimal.ZERO;
        }
        if (soTienCanHoan == null || soTienCanHoan.signum() < 0) {
            soTienCanHoan = BigDecimal.ZERO;
        }
        if (soTienDaHoan == null || soTienDaHoan.signum() < 0) {
            soTienDaHoan = BigDecimal.ZERO;
        }
        if (daCanhBaoChoXacNhan == null) {
            daCanhBaoChoXacNhan = false;
        }
        if (googleMaps == null) {
            googleMaps = false;
        }
    }
}
