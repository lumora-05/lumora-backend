-- Cài đặt hệ thống dùng một bản ghi duy nhất (ma_cai_dat = 1).
-- Backend với spring.jpa.hibernate.ddl-auto=update cũng có thể tự tạo/cập nhật các cột này.
CREATE TABLE IF NOT EXISTS cai_dat_he_thong (
    ma_cai_dat INTEGER PRIMARY KEY,
    ten_nha_hang VARCHAR(120) NOT NULL,
    dia_chi VARCHAR(255),
    so_dien_thoai VARCHAR(30),
    email VARCHAR(120),
    gio_mo_cua VARCHAR(100),
    reservation_url VARCHAR(255),
    menu_url VARCHAR(255),
    logo_url VARCHAR(1000),
    banner_url VARCHAR(1000),
    reservation_default_duration_minutes INTEGER,
    reservation_preparation_minutes INTEGER,
    reservation_no_show_grace_minutes INTEGER,
    delivery_tier1_distance_km DOUBLE PRECISION,
    delivery_tier2_distance_km DOUBLE PRECISION,
    delivery_max_distance_km DOUBLE PRECISION,
    delivery_tier1_fee NUMERIC(18,2),
    delivery_tier2_fee NUMERIC(18,2),
    delivery_tier3_fee NUMERIC(18,2),
    vietqr_bank_id VARCHAR(30),
    vietqr_bank_name VARCHAR(120),
    vietqr_account_no VARCHAR(50),
    vietqr_account_name VARCHAR(160),
    vietqr_template VARCHAR(30),
    vietqr_description_prefix VARCHAR(50),
    loyalty_money_per_earned_point NUMERIC(18,2),
    loyalty_value_per_redeemed_point NUMERIC(18,2),
    loyalty_minimum_redeem_points INTEGER,
    loyalty_maximum_redeem_ratio NUMERIC(6,4),
    chatbot_enabled BOOLEAN,
    chatbot_model VARCHAR(120),
    chatbot_timeout_seconds INTEGER,
    chatbot_max_output_tokens INTEGER,
    chatbot_max_history_messages INTEGER,
    chatbot_minimum_confidence NUMERIC(5,4),
    ngay_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Hỗ trợ database đã có bảng từ phiên bản trước.
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS reservation_default_duration_minutes INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS reservation_preparation_minutes INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS reservation_no_show_grace_minutes INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS delivery_tier1_distance_km DOUBLE PRECISION;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS delivery_tier2_distance_km DOUBLE PRECISION;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS delivery_max_distance_km DOUBLE PRECISION;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS delivery_tier1_fee NUMERIC(18,2);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS delivery_tier2_fee NUMERIC(18,2);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS delivery_tier3_fee NUMERIC(18,2);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS vietqr_bank_id VARCHAR(30);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS vietqr_bank_name VARCHAR(120);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS vietqr_account_no VARCHAR(50);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS vietqr_account_name VARCHAR(160);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS vietqr_template VARCHAR(30);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS vietqr_description_prefix VARCHAR(50);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS loyalty_money_per_earned_point NUMERIC(18,2);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS loyalty_value_per_redeemed_point NUMERIC(18,2);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS loyalty_minimum_redeem_points INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS loyalty_maximum_redeem_ratio NUMERIC(6,4);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS chatbot_enabled BOOLEAN;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS chatbot_model VARCHAR(120);
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS chatbot_timeout_seconds INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS chatbot_max_output_tokens INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS chatbot_max_history_messages INTEGER;
ALTER TABLE cai_dat_he_thong ADD COLUMN IF NOT EXISTS chatbot_minimum_confidence NUMERIC(5,4);
