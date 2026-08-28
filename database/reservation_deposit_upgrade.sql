-- Bổ sung cọc bắt buộc cho nghiệp vụ đặt bàn (PostgreSQL).
-- Chạy script này khi production không dùng spring.jpa.hibernate.ddl-auto=update.

-- 1) Dữ liệu cọc trên từng lịch đặt bàn.
-- Các lịch cũ được xem là legacy: đã thanh toán 0đ để không làm hỏng lịch đang tồn tại.
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS tien_coc NUMERIC(12,2);
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS trang_thai_coc VARCHAR(30);
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS tien_coc_da_khau_tru NUMERIC(12,2);
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS thoi_han_thanh_toan_coc TIMESTAMP NULL;
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS thoi_gian_thanh_toan_coc TIMESTAMP NULL;
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS ma_giao_dich_coc VARCHAR(100) NULL;
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS nguoi_xac_nhan_coc INTEGER NULL;
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS thoi_gian_hoan_coc TIMESTAMP NULL;
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS nguoi_hoan_coc INTEGER NULL;
ALTER TABLE dat_ban ADD COLUMN IF NOT EXISTS ly_do_xu_ly_coc VARCHAR(500) NULL;

UPDATE dat_ban SET tien_coc = 0 WHERE tien_coc IS NULL;
UPDATE dat_ban SET tien_coc_da_khau_tru = 0 WHERE tien_coc_da_khau_tru IS NULL;
UPDATE dat_ban SET trang_thai_coc = 'DA_THANH_TOAN' WHERE trang_thai_coc IS NULL;

ALTER TABLE dat_ban ALTER COLUMN tien_coc SET NOT NULL;
ALTER TABLE dat_ban ALTER COLUMN tien_coc SET DEFAULT 100000;
ALTER TABLE dat_ban ALTER COLUMN tien_coc_da_khau_tru SET NOT NULL;
ALTER TABLE dat_ban ALTER COLUMN tien_coc_da_khau_tru SET DEFAULT 0;
ALTER TABLE dat_ban ALTER COLUMN trang_thai_coc SET NOT NULL;
ALTER TABLE dat_ban ALTER COLUMN trang_thai_coc SET DEFAULT 'CHO_THANH_TOAN';

CREATE UNIQUE INDEX IF NOT EXISTS uq_dat_ban_ma_giao_dich_coc
    ON dat_ban (UPPER(ma_giao_dich_coc))
    WHERE ma_giao_dich_coc IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dat_ban_nguoi_xac_nhan_coc') THEN
        ALTER TABLE dat_ban ADD CONSTRAINT fk_dat_ban_nguoi_xac_nhan_coc
            FOREIGN KEY (nguoi_xac_nhan_coc) REFERENCES nhan_vien(ma_nhan_vien);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dat_ban_nguoi_hoan_coc') THEN
        ALTER TABLE dat_ban ADD CONSTRAINT fk_dat_ban_nguoi_hoan_coc
            FOREIGN KEY (nguoi_hoan_coc) REFERENCES nhan_vien(ma_nhan_vien);
    END IF;
END $$;

ALTER TABLE dat_ban DROP CONSTRAINT IF EXISTS ck_dat_ban_trang_thai;
ALTER TABLE dat_ban ADD CONSTRAINT ck_dat_ban_trang_thai CHECK (
    trang_thai IN (
        'CHO_XAC_NHAN', 'DA_XAC_NHAN', 'KHACH_DA_DEN', 'DA_XEP_BAN',
        'HOAN_THANH', 'DA_HUY', 'TU_CHOI', 'KHONG_DEN', 'HET_HAN'
    )
);

ALTER TABLE dat_ban DROP CONSTRAINT IF EXISTS ck_dat_ban_trang_thai_coc;
ALTER TABLE dat_ban ADD CONSTRAINT ck_dat_ban_trang_thai_coc CHECK (
    trang_thai_coc IN (
        'CHO_THANH_TOAN', 'DA_THANH_TOAN', 'CHO_HOAN', 'DA_HOAN',
        'MAT_COC', 'DA_KHAU_TRU', 'DA_HUY'
    )
);

ALTER TABLE dat_ban DROP CONSTRAINT IF EXISTS ck_dat_ban_tien_coc;
ALTER TABLE dat_ban ADD CONSTRAINT ck_dat_ban_tien_coc CHECK (
    tien_coc >= 0 AND tien_coc_da_khau_tru >= 0 AND tien_coc_da_khau_tru <= tien_coc
);

-- 2) Cấu hình cọc để Admin chỉnh, không phụ thuộc số người.
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS reservation_deposit_amount NUMERIC(18,2);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS reservation_deposit_payment_timeout_minutes INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS reservation_deposit_refund_advance_minutes INTEGER;

UPDATE cai_dat_he_thong SET reservation_deposit_amount = 100000
WHERE reservation_deposit_amount IS NULL OR reservation_deposit_amount <= 0;
UPDATE cai_dat_he_thong SET reservation_deposit_payment_timeout_minutes = 10
WHERE reservation_deposit_payment_timeout_minutes IS NULL OR reservation_deposit_payment_timeout_minutes <= 0;
UPDATE cai_dat_he_thong SET reservation_deposit_refund_advance_minutes = 120
WHERE reservation_deposit_refund_advance_minutes IS NULL OR reservation_deposit_refund_advance_minutes < 0;

-- 3) Hóa đơn lưu riêng khoản cọc đã khấu trừ để tổng doanh thu vẫn là giá trị đầy đủ của hóa đơn.
ALTER TABLE hoa_don ADD COLUMN IF NOT EXISTS tien_coc_da_khau_tru NUMERIC(12,2) DEFAULT 0;
UPDATE hoa_don SET tien_coc_da_khau_tru = 0 WHERE tien_coc_da_khau_tru IS NULL;
ALTER TABLE hoa_don ALTER COLUMN tien_coc_da_khau_tru SET DEFAULT 0;
