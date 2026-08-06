-- Nâng cấp chức năng khách đặt món giao tận nơi theo mô hình:
-- khách đặt trên website -> thu ngân xác nhận -> bếp chế biến -> hệ thống tạo mã vận chuyển
-- -> thu ngân bàn giao cho người giao hàng bên ngoài -> giao thành công/thất bại.
-- Không thêm vai trò SHIPPER.

BEGIN;

ALTER TABLE don_hang
    ADD COLUMN IF NOT EXISTS loai_don VARCHAR(20) NOT NULL DEFAULT 'TAI_BAN';

ALTER TABLE don_hang
    ADD COLUMN IF NOT EXISTS nguon_don VARCHAR(30) NOT NULL DEFAULT 'WEBSITE';

UPDATE don_hang
SET loai_don = 'TAI_BAN'
WHERE loai_don IS NULL OR btrim(loai_don) = '';

UPDATE don_hang
SET nguon_don = 'WEBSITE'
WHERE nguon_don IS NULL OR btrim(nguon_don) = '';

-- Đơn giao hàng không gắn với bàn ăn.
ALTER TABLE don_hang
    ALTER COLUMN ma_ban DROP NOT NULL;

CREATE TABLE IF NOT EXISTS giao_hang_don_hang (
    ma_giao_hang BIGSERIAL PRIMARY KEY,
    ma_don_hang INTEGER NOT NULL,
    tracking_token VARCHAR(80) NOT NULL,
    client_request_id VARCHAR(100),
    ma_van_chuyen VARCHAR(50),
    ten_nguoi_nhan VARCHAR(120) NOT NULL,
    so_dien_thoai_nhan VARCHAR(20) NOT NULL,
    dia_chi_giao_hang VARCHAR(500) NOT NULL,
    khu_vuc_giao_hang VARCHAR(30) NOT NULL,
    ghi_chu_giao_hang VARCHAR(500),
    phi_giao_hang NUMERIC(12,2) NOT NULL DEFAULT 0,
    phuong_thuc_thanh_toan VARCHAR(20) NOT NULL,
    trang_thai_thanh_toan VARCHAR(30) NOT NULL DEFAULT 'CHO_THANH_TOAN',
    ma_giao_dich VARCHAR(100),
    so_tien_da_thanh_toan NUMERIC(12,2) NOT NULL DEFAULT 0,
    ghi_chu_thanh_toan VARCHAR(500),
    trang_thai_giao_hang VARCHAR(30) NOT NULL DEFAULT 'CHO_XAC_NHAN',
    don_vi_van_chuyen VARCHAR(120),
    ten_nguoi_giao VARCHAR(120),
    so_dien_thoai_nguoi_giao VARCHAR(20),
    ghi_chu_ban_giao VARCHAR(500),
    ly_do_tu_choi VARCHAR(500),
    ly_do_giao_that_bai VARCHAR(500),
    thoi_gian_xac_nhan TIMESTAMP,
    thoi_gian_san_sang TIMESTAMP,
    thoi_gian_ban_giao TIMESTAMP,
    thoi_gian_giao_thanh_cong TIMESTAMP,
    thoi_gian_huy TIMESTAMP,
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_cap_nhat TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bổ sung cột nếu bảng đã được Hibernate tạo từ một phiên bản thử nghiệm trước đó.
-- Các cột bắt buộc được thêm dưới dạng nullable để không làm hỏng dữ liệu thử nghiệm cũ;
-- bản ghi mới vẫn được backend kiểm tra đầy đủ trước khi lưu.
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ma_don_hang INTEGER;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS tracking_token VARCHAR(80);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(100);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ma_van_chuyen VARCHAR(50);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ten_nguoi_nhan VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_dien_thoai_nhan VARCHAR(20);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS dia_chi_giao_hang VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS khu_vuc_giao_hang VARCHAR(30);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ghi_chu_giao_hang VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS phi_giao_hang NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS phuong_thuc_thanh_toan VARCHAR(20);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS trang_thai_thanh_toan VARCHAR(30) NOT NULL DEFAULT 'CHO_THANH_TOAN';
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ma_giao_dich VARCHAR(100);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_tien_da_thanh_toan NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ghi_chu_thanh_toan VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS trang_thai_giao_hang VARCHAR(30) NOT NULL DEFAULT 'CHO_XAC_NHAN';
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS don_vi_van_chuyen VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ten_nguoi_giao VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_dien_thoai_nguoi_giao VARCHAR(20);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ghi_chu_ban_giao VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ly_do_tu_choi VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ly_do_giao_that_bai VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_xac_nhan TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_san_sang TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_ban_giao TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_giao_thanh_cong TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_huy TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_cap_nhat TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_giao_hang_don_hang'
    ) THEN
        ALTER TABLE giao_hang_don_hang
            ADD CONSTRAINT fk_giao_hang_don_hang
            FOREIGN KEY (ma_don_hang)
            REFERENCES don_hang (ma_don_hang)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_giao_hang_don_hang_order
    ON giao_hang_don_hang (ma_don_hang);

CREATE UNIQUE INDEX IF NOT EXISTS ux_giao_hang_tracking_token
    ON giao_hang_don_hang (tracking_token);

CREATE UNIQUE INDEX IF NOT EXISTS ux_giao_hang_ma_van_chuyen
    ON giao_hang_don_hang (ma_van_chuyen)
    WHERE ma_van_chuyen IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_giao_hang_client_request
    ON giao_hang_don_hang (client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_giao_hang_ma_giao_dich_ci
    ON giao_hang_don_hang (upper(ma_giao_dich))
    WHERE ma_giao_dich IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_giao_hang_trang_thai
    ON giao_hang_don_hang (trang_thai_giao_hang, thoi_gian_tao DESC);

CREATE INDEX IF NOT EXISTS idx_don_hang_loai_trang_thai
    ON don_hang (loai_don, trang_thai, thoi_gian_dat DESC);

ALTER TABLE hoa_don
    ADD COLUMN IF NOT EXISTS phi_giao_hang NUMERIC(12,2) NOT NULL DEFAULT 0;

COMMIT;
