-- Nâng cấp nghiệp vụ đặt bàn cho PostgreSQL.
-- Có thể chạy thủ công khi môi trường production không dùng ddl-auto=update.

CREATE TABLE IF NOT EXISTS dat_ban (
    ma_dat_ban SERIAL PRIMARY KEY,
    ma_tra_cuu VARCHAR(24) NOT NULL UNIQUE,
    ho_ten_khach VARCHAR(100) NOT NULL,
    so_dien_thoai VARCHAR(20) NOT NULL,
    ngay_gio_den TIMESTAMP NOT NULL,
    thoi_gian_ket_thuc_du_kien TIMESTAMP NOT NULL,
    thoi_luong_phut INTEGER NOT NULL DEFAULT 120,
    so_luong_khach INTEGER NOT NULL,
    khu_vuc_mong_muon VARCHAR(100) NULL,
    ma_ban_du_kien INTEGER NULL,
    ma_ban_thuc_te INTEGER NULL,
    ma_don_hang INTEGER NULL UNIQUE,
    ghi_chu VARCHAR(500) NULL,
    trang_thai VARCHAR(30) NOT NULL DEFAULT 'CHO_XAC_NHAN',
    ly_do_huy_tu_choi VARCHAR(500) NULL,
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_xac_nhan TIMESTAMP NULL,
    thoi_gian_check_in TIMESTAMP NULL,
    thoi_gian_xep_ban TIMESTAMP NULL,
    thoi_gian_hoan_thanh TIMESTAMP NULL,
    nguoi_xac_nhan INTEGER NULL,
    nguoi_check_in INTEGER NULL,
    nguoi_xep_ban INTEGER NULL,
    CONSTRAINT fk_dat_ban_ban_du_kien
        FOREIGN KEY (ma_ban_du_kien) REFERENCES ban_an(ma_ban),
    CONSTRAINT fk_dat_ban_ban_thuc_te
        FOREIGN KEY (ma_ban_thuc_te) REFERENCES ban_an(ma_ban),
    CONSTRAINT fk_dat_ban_don_hang
        FOREIGN KEY (ma_don_hang) REFERENCES don_hang(ma_don_hang),
    CONSTRAINT fk_dat_ban_nguoi_xac_nhan
        FOREIGN KEY (nguoi_xac_nhan) REFERENCES nhan_vien(ma_nhan_vien),
    CONSTRAINT fk_dat_ban_nguoi_check_in
        FOREIGN KEY (nguoi_check_in) REFERENCES nhan_vien(ma_nhan_vien),
    CONSTRAINT fk_dat_ban_nguoi_xep_ban
        FOREIGN KEY (nguoi_xep_ban) REFERENCES nhan_vien(ma_nhan_vien),
    CONSTRAINT ck_dat_ban_trang_thai CHECK (
        trang_thai IN (
            'CHO_XAC_NHAN', 'DA_XAC_NHAN', 'KHACH_DA_DEN', 'DA_XEP_BAN',
            'HOAN_THANH', 'DA_HUY', 'TU_CHOI', 'KHONG_DEN', 'HET_HAN'
        )
    ),
    CONSTRAINT ck_dat_ban_so_luong_khach
        CHECK (so_luong_khach BETWEEN 1 AND 50),
    CONSTRAINT ck_dat_ban_thoi_luong
        CHECK (thoi_luong_phut BETWEEN 30 AND 360),
    CONSTRAINT ck_dat_ban_khoang_thoi_gian
        CHECK (thoi_gian_ket_thuc_du_kien > ngay_gio_den)
);

CREATE INDEX IF NOT EXISTS idx_dat_ban_ngay_gio
    ON dat_ban(ngay_gio_den);
CREATE INDEX IF NOT EXISTS idx_dat_ban_trang_thai
    ON dat_ban(trang_thai);
CREATE INDEX IF NOT EXISTS idx_dat_ban_so_dien_thoai
    ON dat_ban(so_dien_thoai);
CREATE INDEX IF NOT EXISTS idx_dat_ban_ban_du_kien
    ON dat_ban(ma_ban_du_kien);
CREATE INDEX IF NOT EXISTS idx_dat_ban_ban_thuc_te
    ON dat_ban(ma_ban_thuc_te);

-- Trùng lịch được kiểm tra trong transaction khi backend khóa bản ghi ban_an.
-- Không tạo exclusion constraint để script không phụ thuộc extension btree_gist.
