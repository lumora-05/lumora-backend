-- Bổ sung thông báo tồn kho thấp cho Admin.
-- PostgreSQL. Có thể bỏ qua nếu đang dùng spring.jpa.hibernate.ddl-auto=update.

CREATE TABLE IF NOT EXISTS thong_bao_admin (
    ma_thong_bao BIGSERIAL PRIMARY KEY,
    loai_thong_bao VARCHAR(50) NOT NULL,
    tieu_de VARCHAR(200) NOT NULL,
    noi_dung VARCHAR(500) NOT NULL,
    ma_nguyen_lieu INTEGER,
    ten_nguyen_lieu VARCHAR(150),
    so_luong_ton NUMERIC(15, 3),
    muc_ton_toi_thieu NUMERIC(15, 3),
    don_vi_tinh VARCHAR(30),
    trang_thai_ton_kho VARCHAR(30),
    da_doc BOOLEAN NOT NULL DEFAULT FALSE,
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_doc TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_thong_bao_admin_da_doc
    ON thong_bao_admin (da_doc);

CREATE INDEX IF NOT EXISTS idx_thong_bao_admin_thoi_gian
    ON thong_bao_admin (thoi_gian_tao);

CREATE INDEX IF NOT EXISTS idx_thong_bao_admin_nguyen_lieu
    ON thong_bao_admin (ma_nguyen_lieu);
