-- Cho phép giao dịch payOS của đơn online do chính khách khởi tạo QR, không có nhân viên mở thanh toán.
ALTER TABLE giao_dich_payos
    ALTER COLUMN ma_nhan_vien DROP NOT NULL;
