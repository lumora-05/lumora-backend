-- Tùy chọn: chạy thủ công nếu không dùng spring.jpa.hibernate.ddl-auto=update.
CREATE TABLE IF NOT EXISTS hoat_dong_he_thong (
    ma_hoat_dong BIGSERIAL PRIMARY KEY,
    loai_hoat_dong VARCHAR(50) NOT NULL,
    noi_dung VARCHAR(500) NOT NULL,
    doi_tuong_id INTEGER,
    nguoi_thuc_hien VARCHAR(100),
    thoi_gian TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hoat_dong_thoi_gian
    ON hoat_dong_he_thong(thoi_gian DESC);
