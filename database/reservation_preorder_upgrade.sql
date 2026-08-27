-- Nâng cấp chức năng đặt món trước gắn với lịch đặt bàn.
-- PostgreSQL. Có thể bỏ qua nếu môi trường dùng spring.jpa.hibernate.ddl-auto=update.

ALTER TABLE dat_ban
    ADD COLUMN IF NOT EXISTS trang_thai_dat_mon_truoc VARCHAR(30) DEFAULT 'CHUA_DAT',
    ADD COLUMN IF NOT EXISTS ghi_chu_dat_mon_truoc VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ly_do_tu_choi_dat_mon_truoc VARCHAR(500),
    ADD COLUMN IF NOT EXISTS thoi_gian_dat_mon_truoc TIMESTAMP,
    ADD COLUMN IF NOT EXISTS thoi_gian_xac_nhan_mon_truoc TIMESTAMP,
    ADD COLUMN IF NOT EXISTS thoi_gian_du_kien_chuyen_bep TIMESTAMP,
    ADD COLUMN IF NOT EXISTS thoi_gian_chuyen_bep TIMESTAMP,
    ADD COLUMN IF NOT EXISTS nguoi_xac_nhan_mon_truoc INTEGER,
    ADD COLUMN IF NOT EXISTS can_duyet_lai_dat_mon_truoc BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS thoi_gian_thay_doi_dat_mon_truoc TIMESTAMP;

UPDATE dat_ban
SET trang_thai_dat_mon_truoc = 'CHUA_DAT'
WHERE trang_thai_dat_mon_truoc IS NULL OR BTRIM(trang_thai_dat_mon_truoc) = '';

UPDATE dat_ban
SET can_duyet_lai_dat_mon_truoc = FALSE
WHERE can_duyet_lai_dat_mon_truoc IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_dat_ban_nguoi_xac_nhan_mon_truoc'
    ) THEN
        ALTER TABLE dat_ban
            ADD CONSTRAINT fk_dat_ban_nguoi_xac_nhan_mon_truoc
            FOREIGN KEY (nguoi_xac_nhan_mon_truoc)
            REFERENCES nhan_vien(ma_nhan_vien);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS chi_tiet_dat_mon_truoc (
    ma_chi_tiet_dat_mon_truoc SERIAL PRIMARY KEY,
    ma_dat_ban INTEGER NOT NULL,
    ma_mon_an INTEGER NOT NULL,
    so_luong INTEGER NOT NULL CHECK (so_luong > 0),
    don_gia NUMERIC(12,2) NOT NULL CHECK (don_gia >= 0),
    ghi_chu VARCHAR(255),
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dat_mon_truoc_dat_ban
        FOREIGN KEY (ma_dat_ban) REFERENCES dat_ban(ma_dat_ban) ON DELETE CASCADE,
    CONSTRAINT fk_dat_mon_truoc_mon_an
        FOREIGN KEY (ma_mon_an) REFERENCES mon_an(ma_mon_an)
);

CREATE INDEX IF NOT EXISTS idx_dat_mon_truoc_dat_ban
    ON chi_tiet_dat_mon_truoc(ma_dat_ban);
CREATE INDEX IF NOT EXISTS idx_dat_mon_truoc_mon_an
    ON chi_tiet_dat_mon_truoc(ma_mon_an);
CREATE INDEX IF NOT EXISTS idx_dat_ban_trang_thai_dat_mon_truoc
    ON dat_ban(trang_thai_dat_mon_truoc);
