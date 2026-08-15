-- Tài khoản khách hàng là tùy chọn.
-- Khách vãng lai vẫn đặt món như trước; chỉ khách đã đăng ký mới có mat_khau_hash.

ALTER TABLE khach_hang
    ADD COLUMN IF NOT EXISTS mat_khau_hash VARCHAR(100);

-- Không đặt NOT NULL để giữ nguyên toàn bộ khách hàng thân thiết cũ và hỗ trợ khách vãng lai.
