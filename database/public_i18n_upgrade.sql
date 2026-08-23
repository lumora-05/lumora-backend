-- Bổ sung dữ liệu song ngữ VI/EN cho giao diện khách hàng công khai và menu QR.
-- PostgreSQL / Neon. Có thể chạy nhiều lần an toàn.

ALTER TABLE danh_muc_mon
    ADD COLUMN IF NOT EXISTS ten_danh_muc_en VARCHAR(150),
    ADD COLUMN IF NOT EXISTS mo_ta_en VARCHAR(500);

ALTER TABLE mon_an
    ADD COLUMN IF NOT EXISTS ten_mon_an_en VARCHAR(150),
    ADD COLUMN IF NOT EXISTS mo_ta_en VARCHAR(500);

ALTER TABLE khuyen_mai
    ADD COLUMN IF NOT EXISTS ten_khuyen_mai_en VARCHAR(150),
    ADD COLUMN IF NOT EXISTS mo_ta_en VARCHAR(500);

-- Không tự dịch hàng loạt dữ liệu hiện có để tránh sai tên món/thương hiệu.
-- Chỉ backfill các món hiện đang hiển thị ở trang chủ khi bản tiếng Anh còn trống;
-- các món khác vẫn do Admin bổ sung bản dịch qua form/API create/update.

-- Bổ sung bản dịch cho các món hiện có trên trang chủ nếu dữ liệu tiếng Anh đang trống.
-- Không ghi đè nội dung tiếng Anh mà Admin đã nhập.
UPDATE mon_an
SET ten_mon_an_en = CASE trim(ten_mon_an)
        WHEN 'Cá Hồi Áp Chảo' THEN 'Pan-Seared Salmon'
        WHEN 'Cá hồi áp chảo' THEN 'Pan-Seared Salmon'
        WHEN 'Mì Ý Bò Bằm' THEN 'Spaghetti Bolognese'
        WHEN 'Steak Bò Mỹ' THEN 'U.S. Beef Steak'
        WHEN 'Panna Cotta Dâu Tây' THEN 'Strawberry Panna Cotta'
        ELSE ten_mon_an_en
    END
WHERE (ten_mon_an_en IS NULL OR btrim(ten_mon_an_en) = '')
  AND trim(ten_mon_an) IN ('Cá Hồi Áp Chảo', 'Cá hồi áp chảo', 'Mì Ý Bò Bằm', 'Steak Bò Mỹ', 'Panna Cotta Dâu Tây');

UPDATE mon_an
SET mo_ta_en = CASE
        WHEN trim(ten_mon_an) IN ('Cá Hồi Áp Chảo', 'Cá hồi áp chảo')
             AND btrim(coalesce(mo_ta, '')) = 'Cá hồi Na Uy áp chảo giòn da, sốt bơ chanh và rau mầm tươi.'
            THEN 'Crispy-skin Norwegian salmon with lemon butter sauce and fresh microgreens.'
        WHEN trim(ten_mon_an) = 'Mì Ý Bò Bằm'
             AND btrim(coalesce(mo_ta, '')) = 'Mì Ý dai mềm kết hợp sốt cà chua bò bằm đậm đà, phủ phô mai thơm béo.'
            THEN 'Spaghetti tossed with a rich tomato and minced-beef sauce, topped with savory cheese.'
        WHEN trim(ten_mon_an) = 'Steak Bò Mỹ'
             AND btrim(coalesce(mo_ta, '')) = 'Thịt bò Mỹ áp chảo mềm mọng, đậm vị, dùng kèm rau củ và sốt đặc trưng.'
            THEN 'Juicy pan-seared U.S. beef steak served with vegetables and the restaurant signature sauce.'
        WHEN trim(ten_mon_an) = 'Panna Cotta Dâu Tây'
             AND btrim(coalesce(mo_ta, '')) = 'Panna cotta mềm mịn, béo nhẹ kết hợp sốt dâu tây chua ngọt tươi mát.'
            THEN 'Smooth, lightly creamy panna cotta with a refreshing sweet-tart strawberry sauce.'
        ELSE mo_ta_en
    END
WHERE (mo_ta_en IS NULL OR btrim(mo_ta_en) = '')
  AND trim(ten_mon_an) IN ('Cá Hồi Áp Chảo', 'Cá hồi áp chảo', 'Mì Ý Bò Bằm', 'Steak Bò Mỹ', 'Panna Cotta Dâu Tây');
