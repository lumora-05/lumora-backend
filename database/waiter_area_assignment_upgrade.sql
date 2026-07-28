-- Bổ sung khu vực phụ trách cho nhân viên phục vụ (PostgreSQL).
-- Dự án đang dùng spring.jpa.hibernate.ddl-auto=update nên Hibernate có thể tự thêm cột.
-- Chạy file này thủ công nếu muốn quản lý migration database riêng.

ALTER TABLE nhan_vien
    ADD COLUMN IF NOT EXISTS khu_vuc_phu_trach VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_nhan_vien_khu_vuc_phu_trach
    ON nhan_vien (khu_vuc_phu_trach);

CREATE INDEX IF NOT EXISTS idx_ban_an_khu_vuc
    ON ban_an (khu_vuc);

-- Không tự gán khu vực cho nhân viên cũ để tránh cấp nhầm quyền xem bàn/đơn.
-- Admin cần gán khu_vuc_phu_trach khớp chính xác với ban_an.khu_vuc.
