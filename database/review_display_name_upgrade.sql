-- Bổ sung tên hiển thị tùy chọn cho đánh giá khách hàng.
-- Các đánh giá cũ giữ NULL và frontend sẽ hiển thị "Khách hàng ẩn danh".
ALTER TABLE danh_gia
    ADD COLUMN IF NOT EXISTS ten_hien_thi VARCHAR(50);
