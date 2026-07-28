-- Module Kho nguyên liệu cho PostgreSQL.
-- Dự án đang dùng spring.jpa.hibernate.ddl-auto=update nên Hibernate có thể tự tạo bảng.
-- File này dùng khi muốn tạo cấu trúc thủ công hoặc kiểm soát migration.

CREATE TABLE IF NOT EXISTS nguyen_lieu (
    ma_nguyen_lieu SERIAL PRIMARY KEY,
    ten_nguyen_lieu VARCHAR(150) NOT NULL,
    don_vi_tinh VARCHAR(30) NOT NULL,
    so_luong_ton NUMERIC(15, 3) NOT NULL DEFAULT 0 CHECK (so_luong_ton >= 0),
    muc_ton_toi_thieu NUMERIC(15, 3) NOT NULL DEFAULT 0 CHECK (muc_ton_toi_thieu >= 0),
    gia_nhap NUMERIC(18, 2) CHECK (gia_nhap IS NULL OR gia_nhap >= 0),
    mo_ta VARCHAR(500),
    trang_thai BOOLEAN NOT NULL DEFAULT TRUE,
    ngay_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nguyen_lieu_ten ON nguyen_lieu (ten_nguyen_lieu);
CREATE INDEX IF NOT EXISTS idx_nguyen_lieu_trang_thai ON nguyen_lieu (trang_thai);
CREATE UNIQUE INDEX IF NOT EXISTS uk_nguyen_lieu_ten_lower ON nguyen_lieu (LOWER(ten_nguyen_lieu));

CREATE TABLE IF NOT EXISTS giao_dich_kho (
    ma_giao_dich BIGSERIAL PRIMARY KEY,
    ma_nguyen_lieu INTEGER NOT NULL REFERENCES nguyen_lieu(ma_nguyen_lieu),
    loai_giao_dich VARCHAR(30) NOT NULL,
    so_luong NUMERIC(15, 3) NOT NULL CHECK (so_luong >= 0),
    so_luong_truoc NUMERIC(15, 3) NOT NULL CHECK (so_luong_truoc >= 0),
    so_luong_sau NUMERIC(15, 3) NOT NULL CHECK (so_luong_sau >= 0),
    don_gia_nhap NUMERIC(18, 2) CHECK (don_gia_nhap IS NULL OR don_gia_nhap >= 0),
    ly_do VARCHAR(500),
    ma_ly_do VARCHAR(40),
    ghi_chu VARCHAR(500),
    nguoi_thuc_hien VARCHAR(100) NOT NULL,
    thoi_gian TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_giao_dich_kho_loai
        CHECK (loai_giao_dich IN ('NHAP', 'XUAT', 'DIEU_CHINH', 'TIEU_HUY'))
);

CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_nguyen_lieu ON giao_dich_kho (ma_nguyen_lieu);
CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_loai ON giao_dich_kho (loai_giao_dich);
CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_thoi_gian ON giao_dich_kho (thoi_gian DESC);

CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_ma_ly_do ON giao_dich_kho (ma_ly_do);
