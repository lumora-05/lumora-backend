-- Nâng cấp bảng hóa đơn cho luồng tiền mặt/chuyển khoản và in phiếu VietQR.
-- PostgreSQL: các lệnh IF NOT EXISTS giúp chạy lại an toàn.

ALTER TABLE hoa_don
    ADD COLUMN IF NOT EXISTS thoi_gian_thanh_toan TIMESTAMP,
    ADD COLUMN IF NOT EXISTS tien_khach_dua NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS tien_thua NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS ma_giao_dich VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ghi_chu VARCHAR(255),
    ADD COLUMN IF NOT EXISTS noi_dung_chuyen_khoan VARCHAR(50);

UPDATE hoa_don
SET thoi_gian_thanh_toan = COALESCE(thoi_gian_thanh_toan, thoi_gian_tao)
WHERE thoi_gian_thanh_toan IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_hoa_don_ma_giao_dich
    ON hoa_don (UPPER(ma_giao_dich))
    WHERE ma_giao_dich IS NOT NULL AND BTRIM(ma_giao_dich) <> '';
