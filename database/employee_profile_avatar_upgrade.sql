-- Bổ sung ảnh đại diện cho tài khoản nhân viên.
-- Không bổ sung trường địa chỉ theo yêu cầu hiện tại.
ALTER TABLE nhan_vien
    ADD COLUMN IF NOT EXISTS anh_dai_dien VARCHAR(500);
