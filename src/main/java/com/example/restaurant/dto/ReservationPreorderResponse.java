package com.example.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationPreorderResponse(
        Integer maDatBan,
        String maTraCuu,
        String trangThaiDatBan,
        LocalDateTime ngayGioDen,
        String trangThaiDatMonTruoc,
        String ghiChuDatMonTruoc,
        String lyDoTuChoiDatMonTruoc,
        LocalDateTime thoiGianDatMonTruoc,
        LocalDateTime thoiGianXacNhanMonTruoc,
        LocalDateTime thoiGianDuKienChuyenBep,
        LocalDateTime thoiGianChuyenBep,
        Integer maNguoiXacNhanMonTruoc,
        String tenNguoiXacNhanMonTruoc,
        Integer maDonHang,
        BigDecimal tongTienDuKien,
        List<Item> items
) {
    public record Item(
            Integer maChiTietDatMonTruoc,
            Integer maMonAn,
            String tenMonAn,
            String hinhAnh,
            Integer soLuong,
            BigDecimal donGia,
            BigDecimal thanhTien,
            String ghiChu
    ) {}
}
