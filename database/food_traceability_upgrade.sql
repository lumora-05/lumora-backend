-- Truy xuất an toàn thực phẩm theo lô nguyên liệu (PostgreSQL).
-- Dự án đang dùng spring.jpa.hibernate.ddl-auto=update nên Hibernate có thể tự tạo/cập nhật.
-- File này dùng khi muốn chạy migration thủ công và kiểm soát dữ liệu hiện có.

ALTER TABLE lo_nguyen_lieu
    ADD COLUMN IF NOT EXISTS trang_thai_an_toan VARCHAR(30);

UPDATE lo_nguyen_lieu
SET trang_thai_an_toan = 'AN_TOAN'
WHERE trang_thai_an_toan IS NULL OR BTRIM(trang_thai_an_toan) = '';

ALTER TABLE lo_nguyen_lieu
    ALTER COLUMN trang_thai_an_toan SET DEFAULT 'AN_TOAN';

CREATE INDEX IF NOT EXISTS idx_lo_nguyen_lieu_an_toan
    ON lo_nguyen_lieu (trang_thai_an_toan);

CREATE TABLE IF NOT EXISTS cong_thuc_mon_an (
    ma_cong_thuc BIGSERIAL PRIMARY KEY,
    ma_mon_an INTEGER NOT NULL REFERENCES mon_an(ma_mon_an),
    ma_nguyen_lieu INTEGER NOT NULL REFERENCES nguyen_lieu(ma_nguyen_lieu),
    dinh_luong NUMERIC(15, 3) NOT NULL CHECK (dinh_luong > 0),
    trang_thai BOOLEAN NOT NULL DEFAULT TRUE,
    ngay_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cong_thuc_mon_nguyen_lieu UNIQUE (ma_mon_an, ma_nguyen_lieu)
);

CREATE INDEX IF NOT EXISTS idx_cong_thuc_mon_an_mon
    ON cong_thuc_mon_an (ma_mon_an);
CREATE INDEX IF NOT EXISTS idx_cong_thuc_mon_an_nguyen_lieu
    ON cong_thuc_mon_an (ma_nguyen_lieu);

ALTER TABLE giao_dich_kho
    ADD COLUMN IF NOT EXISTS ma_chi_tiet INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_giao_dich_kho_chi_tiet_don_hang'
    ) THEN
        ALTER TABLE giao_dich_kho
            ADD CONSTRAINT fk_giao_dich_kho_chi_tiet_don_hang
            FOREIGN KEY (ma_chi_tiet) REFERENCES chi_tiet_don_hang(ma_chi_tiet);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_chi_tiet
    ON giao_dich_kho (ma_chi_tiet);

CREATE TABLE IF NOT EXISTS su_dung_lo_nguyen_lieu (
    ma_su_dung BIGSERIAL PRIMARY KEY,
    ma_chi_tiet INTEGER NOT NULL REFERENCES chi_tiet_don_hang(ma_chi_tiet),
    ma_nguyen_lieu INTEGER NOT NULL REFERENCES nguyen_lieu(ma_nguyen_lieu),
    ma_lo BIGINT NOT NULL REFERENCES lo_nguyen_lieu(ma_lo),
    ma_giao_dich BIGINT REFERENCES giao_dich_kho(ma_giao_dich),
    so_luong_su_dung NUMERIC(15, 3) NOT NULL CHECK (so_luong_su_dung > 0),
    trang_thai VARCHAR(30) NOT NULL DEFAULT 'DA_CAP_PHAT',
    nguoi_cap_phat VARCHAR(100) NOT NULL,
    thoi_gian_cap_phat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_su_dung_lo_chi_tiet_nguyen_lieu_lo
        UNIQUE (ma_chi_tiet, ma_nguyen_lieu, ma_lo)
);

CREATE INDEX IF NOT EXISTS idx_su_dung_lo_chi_tiet
    ON su_dung_lo_nguyen_lieu (ma_chi_tiet);
CREATE INDEX IF NOT EXISTS idx_su_dung_lo_nguyen_lieu
    ON su_dung_lo_nguyen_lieu (ma_nguyen_lieu);
CREATE INDEX IF NOT EXISTS idx_su_dung_lo_lo
    ON su_dung_lo_nguyen_lieu (ma_lo);
CREATE INDEX IF NOT EXISTS idx_su_dung_lo_thoi_gian
    ON su_dung_lo_nguyen_lieu (thoi_gian_cap_phat DESC);

CREATE TABLE IF NOT EXISTS su_co_lo_nguyen_lieu (
    ma_su_co BIGSERIAL PRIMARY KEY,
    ma_lo BIGINT NOT NULL REFERENCES lo_nguyen_lieu(ma_lo),
    loai_su_co VARCHAR(50) NOT NULL,
    muc_do VARCHAR(30) NOT NULL,
    ly_do VARCHAR(1000) NOT NULL,
    ghi_chu VARCHAR(1000),
    trang_thai VARCHAR(30) NOT NULL DEFAULT 'MOI',
    nguoi_phat_hien VARCHAR(100) NOT NULL,
    thoi_gian_phat_hien TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nguoi_xu_ly VARCHAR(100),
    thoi_gian_xu_ly TIMESTAMP,
    ket_qua_xu_ly VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_su_co_lo_nguyen_lieu_lo
    ON su_co_lo_nguyen_lieu (ma_lo);
CREATE INDEX IF NOT EXISTS idx_su_co_lo_nguyen_lieu_trang_thai
    ON su_co_lo_nguyen_lieu (trang_thai);
CREATE INDEX IF NOT EXISTS idx_su_co_lo_nguyen_lieu_thoi_gian
    ON su_co_lo_nguyen_lieu (thoi_gian_phat_hien DESC);
