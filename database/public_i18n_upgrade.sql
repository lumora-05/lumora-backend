-- Bổ sung dữ liệu song ngữ VI/EN cho giao diện khách hàng công khai và menu QR.
-- PostgreSQL / Neon. Có thể chạy nhiều lần an toàn.

ALTER TABLE danh_muc_mon
    ADD COLUMN IF NOT EXISTS ten_danh_muc_en VARCHAR(150),
    ADD COLUMN IF NOT EXISTS mo_ta_en VARCHAR(500);

ALTER TABLE mon_an
    ADD COLUMN IF NOT EXISTS ten_mon_an_en VARCHAR(150),
    ADD COLUMN IF NOT EXISTS mo_ta_en VARCHAR(500);

ALTER TABLE khuyen_mai
    ADD COLUMN IF NOT EXISTS ten_khuyen_mai_en VARCHAR(150),
    ADD COLUMN IF NOT EXISTS mo_ta_en VARCHAR(500);

-- Không tự dịch dữ liệu hiện có để tránh sai tên món/thương hiệu.
-- Admin có thể bổ sung bản dịch tiếng Anh qua API create/update sau nâng cấp.
