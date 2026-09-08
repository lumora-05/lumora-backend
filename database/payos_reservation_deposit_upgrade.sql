-- Bảng payment attempt payOS riêng cho tiền cọc đặt bàn.
-- Không thay đổi cấu trúc giao_dich_payos đang dùng cho đơn hàng.

CREATE TABLE IF NOT EXISTS giao_dich_payos_dat_ban (
    ma_giao_dich_payos_dat_ban BIGSERIAL PRIMARY KEY,
    ma_dat_ban INTEGER NOT NULL,
    payos_order_code BIGINT NOT NULL UNIQUE,
    payment_link_id VARCHAR(64) UNIQUE,
    so_tien NUMERIC(12, 2) NOT NULL,
    so_dien_thoai_khach VARCHAR(20),
    noi_dung_chuyen_khoan VARCHAR(25) NOT NULL,
    bin_ngan_hang VARCHAR(20),
    so_tai_khoan VARCHAR(64),
    ten_tai_khoan VARCHAR(160),
    qr_code VARCHAR(2500),
    checkout_url VARCHAR(1000),
    trang_thai VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ma_tham_chieu VARCHAR(100) UNIQUE,
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    het_han_luc TIMESTAMP,
    thoi_gian_thanh_toan TIMESTAMP,
    CONSTRAINT fk_payos_dat_ban_reservation
        FOREIGN KEY (ma_dat_ban) REFERENCES dat_ban(ma_dat_ban)
);

CREATE INDEX IF NOT EXISTS idx_payos_dat_ban
    ON giao_dich_payos_dat_ban(ma_dat_ban);

CREATE INDEX IF NOT EXISTS idx_payos_dat_ban_trang_thai
    ON giao_dich_payos_dat_ban(trang_thai);
