-- Cài đặt hệ thống dùng một bản ghi duy nhất (ma_cai_dat = 1).
-- Backend với spring.jpa.hibernate.ddl-auto=update cũng có thể tự tạo bảng này.
CREATE TABLE IF NOT EXISTS cai_dat_he_thong (
    ma_cai_dat INTEGER PRIMARY KEY,
    ten_nha_hang VARCHAR(120) NOT NULL,
    dia_chi VARCHAR(255),
    so_dien_thoai VARCHAR(30),
    email VARCHAR(120),
    gio_mo_cua VARCHAR(100),
    reservation_url VARCHAR(255),
    menu_url VARCHAR(255),
    logo_url VARCHAR(1000),
    banner_url VARCHAR(1000),
    ngay_tao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
