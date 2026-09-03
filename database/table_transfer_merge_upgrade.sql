-- Nâng cấp chuyển bàn / ghép bàn cho PostgreSQL.
-- Dự án đang dùng spring.jpa.hibernate.ddl-auto=update nên Hibernate có thể tự
-- tạo hai cột này. File SQL được giữ để triển khai chủ động trên môi trường thật.

ALTER TABLE ban_an
    ADD COLUMN IF NOT EXISTS ma_nhom_ban VARCHAR(64);

ALTER TABLE ban_an
    ADD COLUMN IF NOT EXISTS ma_ban_chinh INTEGER;

CREATE INDEX IF NOT EXISTS idx_ban_an_ma_nhom_ban
    ON ban_an (ma_nhom_ban);

CREATE INDEX IF NOT EXISTS idx_ban_an_ma_ban_chinh
    ON ban_an (ma_ban_chinh);

-- Liên kết các đơn của nhiều bàn đang phục vụ để tính chung một bill.
ALTER TABLE don_hang
    ADD COLUMN IF NOT EXISTS ma_nhom_thanh_toan VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_don_hang_ma_nhom_thanh_toan
    ON don_hang (ma_nhom_thanh_toan);
