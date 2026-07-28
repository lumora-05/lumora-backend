-- Nâng cấp luồng gọi thêm món trên PostgreSQL.
-- Backend vẫn dùng một bản ghi don_hang đang mở cho mỗi bàn và phân biệt
-- các lần gọi bằng lan_goi + thoi_gian_them trong chi_tiet_don_hang.

BEGIN;

ALTER TABLE chi_tiet_don_hang
    ADD COLUMN IF NOT EXISTS lan_goi INTEGER;

ALTER TABLE chi_tiet_don_hang
    ADD COLUMN IF NOT EXISTS thoi_gian_them TIMESTAMP;

-- Dữ liệu cũ được xem là lượt gọi đầu tiên.
UPDATE chi_tiet_don_hang
SET lan_goi = 1
WHERE lan_goi IS NULL OR lan_goi < 1;

UPDATE chi_tiet_don_hang c
SET thoi_gian_them = COALESCE(
        c.thoi_gian_them,
        (SELECT d.thoi_gian_dat
         FROM don_hang d
         WHERE d.ma_don_hang = c.ma_don_hang),
        CURRENT_TIMESTAMP
    )
WHERE c.thoi_gian_them IS NULL;

ALTER TABLE chi_tiet_don_hang
    ALTER COLUMN lan_goi SET DEFAULT 1;

ALTER TABLE chi_tiet_don_hang
    ALTER COLUMN thoi_gian_them SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_ctdh_don_lan_goi
    ON chi_tiet_don_hang (ma_don_hang, lan_goi, thoi_gian_them);

CREATE INDEX IF NOT EXISTS idx_don_hang_ban_trang_thai
    ON don_hang (ma_ban, trang_thai, thoi_gian_dat DESC);

COMMIT;

-- Kiểm tra dữ liệu cũ có nhiều đơn đang mở cho cùng một bàn hay không.
-- Nếu truy vấn dưới đây có kết quả, cần xử lý/gộp hoặc đóng các đơn cũ thủ công.
SELECT ma_ban, COUNT(*) AS so_don_dang_mo
FROM don_hang
WHERE trang_thai IN (
    'CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DANG_CHUAN_BI', 'DANG_CHE_BIEN',
    'SAN_SANG', 'SAN_SANG_PHUC_VU', 'DA_HOAN_THANH', 'DA_PHUC_VU',
    'CHO_THANH_TOAN', 'SAN_SANG_THANH_TOAN'
)
GROUP BY ma_ban
HAVING COUNT(*) > 1;
