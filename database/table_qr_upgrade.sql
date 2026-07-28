-- Tùy chọn: chạy thủ công nếu không dùng spring.jpa.hibernate.ddl-auto=update.
ALTER TABLE ban_an ADD COLUMN IF NOT EXISTS khu_vuc VARCHAR(100);
ALTER TABLE ban_an ADD COLUMN IF NOT EXISTS suc_chua INTEGER;
ALTER TABLE ban_an ADD COLUMN IF NOT EXISTS trang_thai_qr VARCHAR(30);
ALTER TABLE ban_an ADD COLUMN IF NOT EXISTS ngay_tao_qr TIMESTAMP;
ALTER TABLE ban_an ADD COLUMN IF NOT EXISTS ngay_cap_nhat_qr TIMESTAMP;
ALTER TABLE ban_an ADD COLUMN IF NOT EXISTS qr_token VARCHAR(64);

UPDATE ban_an SET khu_vuc = 'Khu vực chung' WHERE khu_vuc IS NULL OR TRIM(khu_vuc) = '';
UPDATE ban_an SET suc_chua = 4 WHERE suc_chua IS NULL OR suc_chua < 1;
UPDATE ban_an
SET trang_thai_qr = CASE
    WHEN anh_qr IS NULL OR TRIM(anh_qr) = '' THEN 'CHUA_TAO'
    ELSE 'DANG_HOAT_DONG'
END
WHERE trang_thai_qr IS NULL OR TRIM(trang_thai_qr) = '';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ban_an_ma_qr
    ON ban_an(ma_qr)
    WHERE ma_qr IS NOT NULL;

-- Mỗi bàn có một QR token ngẫu nhiên, duy nhất và ổn định.
UPDATE ban_an
SET qr_token = md5(random()::text || clock_timestamp()::text || ma_ban::text)
WHERE qr_token IS NULL OR TRIM(qr_token) = '';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ban_an_qr_token
    ON ban_an(qr_token);

ALTER TABLE ban_an ALTER COLUMN qr_token SET NOT NULL;
