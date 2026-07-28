-- Bổ sung nghiệp vụ tiêu hủy/hao hụt nguyên liệu cho PostgreSQL.
-- Chạy sau inventory_upgrade.sql và inventory_expiry_upgrade.sql.

ALTER TABLE giao_dich_kho
    ADD COLUMN IF NOT EXISTS ma_ly_do VARCHAR(40);

ALTER TABLE giao_dich_kho
    ADD COLUMN IF NOT EXISTS ghi_chu VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_giao_dich_kho_ma_ly_do
    ON giao_dich_kho (ma_ly_do);

-- Constraint cũ chỉ cho NHAP, XUAT, DIEU_CHINH.
ALTER TABLE giao_dich_kho
    DROP CONSTRAINT IF EXISTS chk_giao_dich_kho_loai;

ALTER TABLE giao_dich_kho
    ADD CONSTRAINT chk_giao_dich_kho_loai
    CHECK (loai_giao_dich IN ('NHAP', 'XUAT', 'DIEU_CHINH', 'TIEU_HUY'));
