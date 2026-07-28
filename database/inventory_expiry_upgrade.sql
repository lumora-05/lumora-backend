-- Bổ sung quản lý lô và hạn sử dụng cho module Kho nguyên liệu (PostgreSQL).
-- Dự án đang dùng spring.jpa.hibernate.ddl-auto=update nên Hibernate có thể tự tạo/cập nhật bảng.
-- Có thể chạy file này thủ công khi muốn kiểm soát migration database.

CREATE TABLE IF NOT EXISTS lo_nguyen_lieu (
    ma_lo BIGSERIAL PRIMARY KEY,
    ma_nguyen_lieu INTEGER NOT NULL REFERENCES nguyen_lieu(ma_nguyen_lieu),
    so_lo VARCHAR(80) NOT NULL,
    ngay_nhap DATE NOT NULL DEFAULT CURRENT_DATE,
    ngay_san_xuat DATE,
    han_su_dung DATE,
    so_luong_ban_dau NUMERIC(15, 3) NOT NULL DEFAULT 0 CHECK (so_luong_ban_dau >= 0),
    so_luong_con_lai NUMERIC(15, 3) NOT NULL DEFAULT 0 CHECK (so_luong_con_lai >= 0),
    don_gia_nhap NUMERIC(18, 2) CHECK (don_gia_nhap IS NULL OR don_gia_nhap >= 0),
    nha_cung_cap VARCHAR(200),
    trang_thai BOOLEAN NOT NULL DEFAULT TRUE,
    ngay_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lo_nguyen_lieu_so_lo UNIQUE (ma_nguyen_lieu, so_lo),
    CONSTRAINT chk_lo_nguyen_lieu_ngay
        CHECK (ngay_san_xuat IS NULL OR han_su_dung IS NULL OR ngay_san_xuat <= han_su_dung)
);

CREATE INDEX IF NOT EXISTS idx_lo_nguyen_lieu_nguyen_lieu
    ON lo_nguyen_lieu (ma_nguyen_lieu);
CREATE INDEX IF NOT EXISTS idx_lo_nguyen_lieu_han_su_dung
    ON lo_nguyen_lieu (han_su_dung);
CREATE INDEX IF NOT EXISTS idx_lo_nguyen_lieu_trang_thai
    ON lo_nguyen_lieu (trang_thai);

ALTER TABLE giao_dich_kho
    ADD COLUMN IF NOT EXISTS ma_lo BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_giao_dich_kho_lo_nguyen_lieu'
    ) THEN
        ALTER TABLE giao_dich_kho
            ADD CONSTRAINT fk_giao_dich_kho_lo_nguyen_lieu
            FOREIGN KEY (ma_lo) REFERENCES lo_nguyen_lieu(ma_lo);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_lo
    ON giao_dich_kho (ma_lo);
