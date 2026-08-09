-- Nâng cấp chức năng khách đặt món giao tận nơi theo mô hình:
-- khách đặt trên website -> Google Places chuẩn hóa địa chỉ -> Routes API tính quãng đường/phí -> xác thực số điện thoại -> thu ngân xác nhận
-- -> COD xuống bếp ngay / VietQR chờ thanh toán rồi xuống bếp -> điều phối tài xế gần khi bếp hoàn tất
-- -> bàn giao -> đối tác gửi webhook kết quả -> thu ngân đối soát.
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
    dia_chi_chi_tiet VARCHAR(500),
    phuong_xa VARCHAR(120),
    quan_huyen VARCHAR(100),
    tinh_thanh VARCHAR(100),
    khu_vuc_giao_hang VARCHAR(30) NOT NULL,
    google_maps BOOLEAN NOT NULL DEFAULT FALSE,
    google_place_id VARCHAR(255),
    quang_duong_met INTEGER,
    thoi_gian_du_kien_giay BIGINT,
    google_route_polyline TEXT,
    ghi_chu_giao_hang VARCHAR(500),
    phi_giao_hang NUMERIC(12,2) NOT NULL DEFAULT 0,
    phuong_thuc_thanh_toan VARCHAR(20) NOT NULL,
    trang_thai_thanh_toan VARCHAR(30) NOT NULL DEFAULT 'CHO_THANH_TOAN',
    ma_giao_dich VARCHAR(100),
    so_tien_da_thanh_toan NUMERIC(12,2) NOT NULL DEFAULT 0,
    ghi_chu_thanh_toan VARCHAR(500),
    so_tien_can_hoan NUMERIC(12,2) NOT NULL DEFAULT 0,
    so_tien_da_hoan NUMERIC(12,2) NOT NULL DEFAULT 0,
    thoi_gian_het_han_thanh_toan TIMESTAMP,
    da_canh_bao_cho_xac_nhan BOOLEAN NOT NULL DEFAULT FALSE,
    trang_thai_giao_hang VARCHAR(30) NOT NULL DEFAULT 'CHO_XAC_NHAN',
    don_vi_van_chuyen VARCHAR(120),
    ten_nguoi_giao VARCHAR(120),
    so_dien_thoai_nguoi_giao VARCHAR(20),
    ghi_chu_ban_giao VARCHAR(500),
    ly_do_tu_choi VARCHAR(500),
    ly_do_giao_that_bai VARCHAR(500),
    trang_thai_doi_tac VARCHAR(40),
    ly_do_doi_tac VARCHAR(500),
    nguon_cap_nhat_doi_tac VARCHAR(40),
    ma_su_kien_doi_tac VARCHAR(120),
    thoi_gian_cap_nhat_doi_tac TIMESTAMP,
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
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS dia_chi_chi_tiet VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS phuong_xa VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS quan_huyen VARCHAR(100);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS tinh_thanh VARCHAR(100);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS khu_vuc_giao_hang VARCHAR(30);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS google_maps BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(255);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS quang_duong_met INTEGER;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_du_kien_giay BIGINT;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS google_route_polyline TEXT;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ghi_chu_giao_hang VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS phi_giao_hang NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS phuong_thuc_thanh_toan VARCHAR(20);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS trang_thai_thanh_toan VARCHAR(30) NOT NULL DEFAULT 'CHO_THANH_TOAN';
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ma_giao_dich VARCHAR(100);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_tien_da_thanh_toan NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ghi_chu_thanh_toan VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_tien_can_hoan NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_tien_da_hoan NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_het_han_thanh_toan TIMESTAMP;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS da_canh_bao_cho_xac_nhan BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS trang_thai_giao_hang VARCHAR(30) NOT NULL DEFAULT 'CHO_XAC_NHAN';
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS don_vi_van_chuyen VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ten_nguoi_giao VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS so_dien_thoai_nguoi_giao VARCHAR(20);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ghi_chu_ban_giao VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ly_do_tu_choi VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ly_do_giao_that_bai VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS trang_thai_doi_tac VARCHAR(40);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ly_do_doi_tac VARCHAR(500);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS nguon_cap_nhat_doi_tac VARCHAR(40);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS ma_su_kien_doi_tac VARCHAR(120);
ALTER TABLE giao_hang_don_hang ADD COLUMN IF NOT EXISTS thoi_gian_cap_nhat_doi_tac TIMESTAMP;
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

CREATE TABLE IF NOT EXISTS hoan_tien_giao_hang (
    ma_hoan_tien BIGSERIAL PRIMARY KEY,
    ma_giao_hang BIGINT NOT NULL,
    so_tien NUMERIC(12,2) NOT NULL,
    ma_giao_dich VARCHAR(100) NOT NULL,
    ghi_chu VARCHAR(500),
    thoi_gian_hoan TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hoan_tien_giao_hang
        FOREIGN KEY (ma_giao_hang) REFERENCES giao_hang_don_hang(ma_giao_hang) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hoan_tien_giao_hang_ma_giao_dich_ci
    ON hoan_tien_giao_hang (upper(ma_giao_dich));

CREATE INDEX IF NOT EXISTS idx_hoan_tien_giao_hang_order
    ON hoan_tien_giao_hang (ma_giao_hang, thoi_gian_hoan DESC);

ALTER TABLE hoa_don
    ADD COLUMN IF NOT EXISTS phi_giao_hang NUMERIC(12,2) NOT NULL DEFAULT 0;

COMMIT;
