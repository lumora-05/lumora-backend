-- Chatbot hỗ trợ khách hàng LUMORA.
-- Hibernate ddl-auto=update có thể tự tạo bảng; tệp này dùng khi triển khai bằng migration thủ công.

CREATE TABLE IF NOT EXISTS phien_chatbot (
    ma_phien BIGSERIAL PRIMARY KEY,
    session_token VARCHAR(64) NOT NULL UNIQUE,
    qr_token VARCHAR(255),
    trang_thai VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_phien_chatbot_last_activity
    ON phien_chatbot(last_activity_at);

CREATE TABLE IF NOT EXISTS tin_nhan_chatbot (
    ma_tin_nhan BIGSERIAL PRIMARY KEY,
    ma_phien BIGINT NOT NULL REFERENCES phien_chatbot(ma_phien) ON DELETE CASCADE,
    vai_tro VARCHAR(20) NOT NULL,
    noi_dung TEXT NOT NULL,
    intent VARCHAR(50),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tin_nhan_chatbot_session
    ON tin_nhan_chatbot(ma_phien);

CREATE INDEX IF NOT EXISTS idx_tin_nhan_chatbot_created
    ON tin_nhan_chatbot(created_at);
