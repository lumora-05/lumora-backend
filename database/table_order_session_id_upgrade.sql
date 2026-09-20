-- Lumora - ID phiên gọi món tại bàn
-- Có thể chạy thủ công nếu production đặt JPA_DDL_AUTO=validate/none.
-- Với cấu hình mặc định JPA_DDL_AUTO=update, Hibernate sẽ tự thêm cột.

ALTER TABLE don_hang
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);

-- Dữ liệu cũ chỉ được gắn ID ổn định để có thể tra cứu.
-- Các đơn/phiên mới do backend tạo sẽ dùng UUID ngẫu nhiên.
UPDATE don_hang
SET session_id = 'LEGACY-' || ma_don_hang
WHERE ma_ban IS NOT NULL
  AND (session_id IS NULL OR BTRIM(session_id) = '');

CREATE UNIQUE INDEX IF NOT EXISTS uk_don_hang_session_id
    ON don_hang(session_id)
    WHERE session_id IS NOT NULL;
