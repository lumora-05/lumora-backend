CREATE TABLE IF NOT EXISTS ma_xac_nhan_dat_lai_mat_khau (
    ma_xac_nhan BIGSERIAL PRIMARY KEY,
    ma_nhan_vien INTEGER NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    otp_het_han_luc TIMESTAMP NOT NULL,
    so_lan_nhap_sai INTEGER NOT NULL DEFAULT 0,
    xac_minh_luc TIMESTAMP NULL,
    reset_token_hash VARCHAR(64) NULL,
    reset_token_het_han_luc TIMESTAMP NULL,
    da_su_dung_luc TIMESTAMP NULL,
    gui_luc TIMESTAMP NOT NULL,
    ngay_tao TIMESTAMP NOT NULL,
    CONSTRAINT fk_reset_code_employee
        FOREIGN KEY (ma_nhan_vien) REFERENCES nhan_vien(ma_nhan_vien)
);

CREATE INDEX IF NOT EXISTS idx_reset_code_employee
    ON ma_xac_nhan_dat_lai_mat_khau(ma_nhan_vien);

CREATE UNIQUE INDEX IF NOT EXISTS idx_reset_code_token
    ON ma_xac_nhan_dat_lai_mat_khau(reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;
