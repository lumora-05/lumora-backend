-- Nâng cấp nghiệp vụ yêu cầu phục vụ tại bàn cho PostgreSQL.
-- Dự án đang dùng spring.jpa.hibernate.ddl-auto=update nên bảng cũng có thể được
-- Hibernate tạo tự động. File này dùng khi triển khai database bằng migration.

CREATE TABLE IF NOT EXISTS yeu_cau_phuc_vu (
    ma_yeu_cau SERIAL PRIMARY KEY,
    ma_ban INTEGER NOT NULL,
    ten_ban VARCHAR(50) NOT NULL,
    khu_vuc VARCHAR(100) NOT NULL DEFAULT 'Khu vực chung',
    loai_yeu_cau VARCHAR(50) NOT NULL,
    noi_dung VARCHAR(500),
    trang_thai VARCHAR(30) NOT NULL DEFAULT 'MOI',
    muc_do_uu_tien VARCHAR(30) NOT NULL DEFAULT 'BINH_THUONG',
    ma_nhan_vien_tiep_nhan INTEGER,
    ten_nhan_vien_tiep_nhan VARCHAR(100),
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_tiep_nhan TIMESTAMP,
    thoi_gian_hoan_thanh TIMESTAMP,
    thoi_gian_huy TIMESTAMP,
    ma_nguoi_huy INTEGER,
    ten_nguoi_huy VARCHAR(100),
    nguon_huy VARCHAR(30),
    ly_do_huy VARCHAR(500),
    phien_ban BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ycpv_trang_thai CHECK (
        trang_thai IN ('MOI', 'DA_TIEP_NHAN', 'HOAN_THANH', 'DA_HUY')
    ),
    CONSTRAINT chk_ycpv_loai CHECK (
        loai_yeu_cau IN (
            'GOI_NHAN_VIEN',
            'THEM_NUOC',
            'THEM_DUNG_CU',
            'THEM_KHAN_GIAY',
            'DON_BAN',
            'YEU_CAU_KHAC'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_ycpv_ban_trang_thai
    ON yeu_cau_phuc_vu (ma_ban, trang_thai);

CREATE INDEX IF NOT EXISTS idx_ycpv_khu_vuc_trang_thai
    ON yeu_cau_phuc_vu (khu_vuc, trang_thai);

CREATE INDEX IF NOT EXISTS idx_ycpv_thoi_gian_tao
    ON yeu_cau_phuc_vu (thoi_gian_tao);
