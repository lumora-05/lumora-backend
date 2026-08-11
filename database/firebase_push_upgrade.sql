-- FCM Web Push cho nhân viên bếp/phục vụ.
CREATE TABLE IF NOT EXISTS nhan_vien_push_device (
    ma_push_device BIGSERIAL PRIMARY KEY,
    ma_nhan_vien INTEGER NOT NULL REFERENCES nhan_vien(ma_nhan_vien) ON DELETE CASCADE,
    firebase_installation_id VARCHAR(512) NOT NULL,
    kenh VARCHAR(30) NOT NULL,
    user_agent VARCHAR(500),
    hoat_dong BOOLEAN NOT NULL DEFAULT TRUE,
    thoi_gian_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thoi_gian_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_push_device_fid_channel UNIQUE(firebase_installation_id, kenh)
);

CREATE INDEX IF NOT EXISTS idx_push_device_channel_active
    ON nhan_vien_push_device(kenh, hoat_dong);

CREATE INDEX IF NOT EXISTS idx_push_device_employee
    ON nhan_vien_push_device(ma_nhan_vien);
