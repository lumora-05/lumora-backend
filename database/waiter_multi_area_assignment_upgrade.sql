-- Nâng cấp phân công phục vụ: một nhân viên WAITER có thể phụ trách nhiều khu vực.
-- Giữ cột nhan_vien.khu_vuc_phu_trach để tương thích với dữ liệu/client cũ.

CREATE TABLE IF NOT EXISTS nhan_vien_khu_vuc (
    ma_nhan_vien INTEGER NOT NULL,
    khu_vuc VARCHAR(100) NOT NULL,
    CONSTRAINT pk_nhan_vien_khu_vuc PRIMARY KEY (ma_nhan_vien, khu_vuc),
    CONSTRAINT fk_nhan_vien_khu_vuc_nhan_vien
        FOREIGN KEY (ma_nhan_vien)
        REFERENCES nhan_vien (ma_nhan_vien)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_nhan_vien_khu_vuc_khu_vuc
    ON nhan_vien_khu_vuc (khu_vuc);

-- Migrate dữ liệu cũ: mỗi phục vụ đang có một khu vực sẽ được thêm vào bảng mới.
INSERT INTO nhan_vien_khu_vuc (ma_nhan_vien, khu_vuc)
SELECT nv.ma_nhan_vien, BTRIM(nv.khu_vuc_phu_trach)
FROM nhan_vien nv
JOIN vai_tro vt ON vt.ma_vai_tro = nv.ma_vai_tro
WHERE UPPER(REPLACE(vt.ten_vai_tro, 'ROLE_', '')) = 'WAITER'
  AND nv.khu_vuc_phu_trach IS NOT NULL
  AND BTRIM(nv.khu_vuc_phu_trach) <> ''
ON CONFLICT (ma_nhan_vien, khu_vuc) DO NOTHING;
