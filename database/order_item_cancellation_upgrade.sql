-- Nâng cấp nghiệp vụ hủy món có lý do và duyệt yêu cầu hủy.
-- PostgreSQL. Có thể chạy nhiều lần an toàn.

ALTER TABLE chi_tiet_don_hang
    ADD COLUMN IF NOT EXISTS trang_thai_huy VARCHAR(30),
    ADD COLUMN IF NOT EXISTS trang_thai_truoc_huy VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ma_ly_do_huy VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ly_do_huy VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ghi_chu_huy VARCHAR(255),
    ADD COLUMN IF NOT EXISTS nguon_yeu_cau_huy VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ma_nguoi_yeu_cau_huy INTEGER,
    ADD COLUMN IF NOT EXISTS thoi_gian_yeu_cau_huy TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ma_nguoi_xu_ly_huy INTEGER,
    ADD COLUMN IF NOT EXISTS thoi_gian_xu_ly_huy TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ghi_chu_xu_ly_huy VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_chi_tiet_don_hang_trang_thai_huy
    ON chi_tiet_don_hang (trang_thai_huy, thoi_gian_yeu_cau_huy DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_ctdh_nguoi_yeu_cau_huy'
    ) THEN
        ALTER TABLE chi_tiet_don_hang
            ADD CONSTRAINT fk_ctdh_nguoi_yeu_cau_huy
            FOREIGN KEY (ma_nguoi_yeu_cau_huy)
            REFERENCES nhan_vien (ma_nhan_vien)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_ctdh_nguoi_xu_ly_huy'
    ) THEN
        ALTER TABLE chi_tiet_don_hang
            ADD CONSTRAINT fk_ctdh_nguoi_xu_ly_huy
            FOREIGN KEY (ma_nguoi_xu_ly_huy)
            REFERENCES nhan_vien (ma_nhan_vien)
            ON DELETE SET NULL;
    END IF;
END $$;
