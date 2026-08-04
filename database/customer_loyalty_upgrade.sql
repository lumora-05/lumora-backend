-- Nâng cấp chức năng khách hàng thân thiết và tích điểm.
-- PostgreSQL. Có thể chạy trước khi deploy; JPA ddl-auto=update cũng có thể tạo cấu trúc mới.

CREATE TABLE IF NOT EXISTS khach_hang (
    ma_khach_hang SERIAL PRIMARY KEY,
    ho_ten VARCHAR(100) NOT NULL,
    so_dien_thoai VARCHAR(15) NOT NULL,
    diem_tich_luy INTEGER NOT NULL DEFAULT 0,
    tong_chi_tieu NUMERIC(14,2) NOT NULL DEFAULT 0,
    trang_thai VARCHAR(20) NOT NULL DEFAULT 'HOAT_DONG',
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    phien_ban BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_khach_hang_so_dien_thoai UNIQUE (so_dien_thoai),
    CONSTRAINT ck_khach_hang_diem_nonnegative CHECK (diem_tich_luy >= 0),
    CONSTRAINT ck_khach_hang_chi_tieu_nonnegative CHECK (tong_chi_tieu >= 0)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_khach_hang_so_dien_thoai') THEN
        ALTER TABLE khach_hang
            ADD CONSTRAINT uk_khach_hang_so_dien_thoai UNIQUE (so_dien_thoai);
    END IF;
END $$;

ALTER TABLE don_hang ADD COLUMN IF NOT EXISTS ma_khach_hang INTEGER;
ALTER TABLE don_hang ADD COLUMN IF NOT EXISTS diem_da_su_dung INTEGER DEFAULT 0;
ALTER TABLE don_hang ADD COLUMN IF NOT EXISTS tien_giam_tu_diem NUMERIC(12,2) DEFAULT 0;
ALTER TABLE don_hang ADD COLUMN IF NOT EXISTS diem_duoc_cong INTEGER DEFAULT 0;

UPDATE don_hang SET diem_da_su_dung = 0 WHERE diem_da_su_dung IS NULL;
UPDATE don_hang SET tien_giam_tu_diem = 0 WHERE tien_giam_tu_diem IS NULL;
UPDATE don_hang SET diem_duoc_cong = 0 WHERE diem_duoc_cong IS NULL;

ALTER TABLE hoa_don ADD COLUMN IF NOT EXISTS ma_khach_hang INTEGER;
ALTER TABLE hoa_don ADD COLUMN IF NOT EXISTS diem_da_su_dung INTEGER DEFAULT 0;
ALTER TABLE hoa_don ADD COLUMN IF NOT EXISTS tien_giam_tu_diem NUMERIC(12,2) DEFAULT 0;
ALTER TABLE hoa_don ADD COLUMN IF NOT EXISTS diem_duoc_cong INTEGER DEFAULT 0;

UPDATE hoa_don SET diem_da_su_dung = 0 WHERE diem_da_su_dung IS NULL;
UPDATE hoa_don SET tien_giam_tu_diem = 0 WHERE tien_giam_tu_diem IS NULL;
UPDATE hoa_don SET diem_duoc_cong = 0 WHERE diem_duoc_cong IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_don_hang_khach_hang') THEN
        ALTER TABLE don_hang
            ADD CONSTRAINT fk_don_hang_khach_hang
            FOREIGN KEY (ma_khach_hang) REFERENCES khach_hang(ma_khach_hang);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_hoa_don_khach_hang') THEN
        ALTER TABLE hoa_don
            ADD CONSTRAINT fk_hoa_don_khach_hang
            FOREIGN KEY (ma_khach_hang) REFERENCES khach_hang(ma_khach_hang);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_don_hang_ma_khach_hang ON don_hang(ma_khach_hang);
CREATE INDEX IF NOT EXISTS idx_hoa_don_ma_khach_hang ON hoa_don(ma_khach_hang);

CREATE TABLE IF NOT EXISTS giao_dich_diem (
    ma_giao_dich_diem BIGSERIAL PRIMARY KEY,
    ma_khach_hang INTEGER NOT NULL,
    ma_don_hang INTEGER,
    loai_giao_dich VARCHAR(20) NOT NULL,
    so_diem INTEGER NOT NULL,
    so_du_sau_giao_dich INTEGER NOT NULL,
    noi_dung VARCHAR(255) NOT NULL,
    thoi_gian TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_giao_dich_diem_khach_hang
        FOREIGN KEY (ma_khach_hang) REFERENCES khach_hang(ma_khach_hang),
    CONSTRAINT fk_giao_dich_diem_don_hang
        FOREIGN KEY (ma_don_hang) REFERENCES don_hang(ma_don_hang),
    CONSTRAINT uk_giao_dich_diem_don_loai UNIQUE (ma_don_hang, loai_giao_dich),
    CONSTRAINT ck_giao_dich_diem_so_du_nonnegative CHECK (so_du_sau_giao_dich >= 0)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_giao_dich_diem_khach_hang') THEN
        ALTER TABLE giao_dich_diem
            ADD CONSTRAINT fk_giao_dich_diem_khach_hang
            FOREIGN KEY (ma_khach_hang) REFERENCES khach_hang(ma_khach_hang);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_giao_dich_diem_don_hang') THEN
        ALTER TABLE giao_dich_diem
            ADD CONSTRAINT fk_giao_dich_diem_don_hang
            FOREIGN KEY (ma_don_hang) REFERENCES don_hang(ma_don_hang);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_giao_dich_diem_don_loai') THEN
        ALTER TABLE giao_dich_diem
            ADD CONSTRAINT uk_giao_dich_diem_don_loai UNIQUE (ma_don_hang, loai_giao_dich);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_giao_dich_diem_khach_thoi_gian
    ON giao_dich_diem(ma_khach_hang, thoi_gian DESC);
