package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ban_an")
public class DiningTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_ban")
    private Integer maBan;

    @Column(name = "ten_ban", length = 50, nullable = false)
    private String tenBan;

    /**
     * Mã hiển thị của QR, ví dụ QR0001. Nội dung thật trong ảnh QR dùng
     * qrToken riêng của bàn.
     */
    @Column(name = "ma_qr", length = 50, unique = true)
    private String maQr;

    /**
     * Token ngẫu nhiên riêng của từng bàn. Token này được mã hóa trong ảnh QR và
     * không thay đổi khi admin tạo lại ảnh QR.
     */
    @Column(name = "qr_token", length = 64, unique = true)
    private String qrToken;

    /** Đường dẫn ảnh QR được public qua /uploads/qrcodes/**. */
    @Column(name = "anh_qr", length = 500)
    private String anhQr;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai = "TRONG";

    @Column(name = "ghi_chu", length = 255)
    private String ghiChu;

    @Column(name = "khu_vuc", length = 100)
    private String khuVuc = "Khu vực chung";

    @Column(name = "suc_chua")
    private Integer sucChua = 4;

    /** Mã nhóm khi nhiều bàn được ghép để dùng chung một đơn. */
    @Column(name = "ma_nhom_ban", length = 64)
    private String maNhomBan;

    /** Mã bàn chính của nhóm; null khi bàn không tham gia ghép bàn. */
    @Column(name = "ma_ban_chinh")
    private Integer maBanChinh;

    /** DANG_HOAT_DONG, TAM_NGUNG, NGUNG_SU_DUNG hoặc CHUA_TAO. */
    @Column(name = "trang_thai_qr", length = 30)
    private String trangThaiQr = "CHUA_TAO";

    @Column(name = "ngay_tao_qr")
    private LocalDateTime ngayTaoQr;

    @Column(name = "ngay_cap_nhat_qr")
    private LocalDateTime ngayCapNhatQr;

    @Transient
    public boolean isDangGhepBan() {
        return maNhomBan != null && !maNhomBan.isBlank() && maBanChinh != null;
    }

    @Transient
    public boolean isLaBanChinh() {
        return isDangGhepBan() && maBan != null && maBan.equals(maBanChinh);
    }

    @Transient
    public String getVaiTroTrongNhom() {
        if (!isDangGhepBan()) {
            return null;
        }
        return isLaBanChinh() ? "BAN_CHINH" : "BAN_GHEP";
    }

    @PrePersist
    void prePersist() {
        if (qrToken == null || qrToken.isBlank()) {
            qrToken = UUID.randomUUID().toString();
        }
        if (trangThai == null || trangThai.isBlank()) {
            trangThai = "TRONG";
        }
        if (khuVuc == null || khuVuc.isBlank()) {
            khuVuc = "Khu vực chung";
        }
        if (sucChua == null || sucChua < 1) {
            sucChua = 4;
        }
        if (trangThaiQr == null || trangThaiQr.isBlank()) {
            trangThaiQr = anhQr == null || anhQr.isBlank() ? "CHUA_TAO" : "DANG_HOAT_DONG";
        }
    }
}
