-- PostgreSQL: chạy trước khi khởi động nếu JPA_DDL_AUTO=validate/none.
-- Với cấu hình mặc định update: Hibernate thêm cột/ràng buộc;
-- DataInitializer gỡ NOT NULL của SĐT. Không xóa hoặc ghép dữ liệu cũ.
BEGIN;
ALTER TABLE khach_hang ADD COLUMN IF NOT EXISTS google_subject VARCHAR(255);
ALTER TABLE khach_hang ADD COLUMN IF NOT EXISTS google_email VARCHAR(320);
ALTER TABLE khach_hang ALTER COLUMN so_dien_thoai DROP NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_khach_hang_google_subject'
          AND conrelid = 'khach_hang'::regclass
    ) THEN
        ALTER TABLE khach_hang ADD CONSTRAINT uk_khach_hang_google_subject UNIQUE (google_subject);
    END IF;
END $$;
COMMIT;
