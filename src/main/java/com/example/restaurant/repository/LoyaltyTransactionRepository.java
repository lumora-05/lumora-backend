package com.example.restaurant.repository;

import com.example.restaurant.entity.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {
    Page<LoyaltyTransaction> findByKhachHang_MaKhachHangOrderByThoiGianDescMaGiaoDichDiemDesc(
            Integer maKhachHang,
            Pageable pageable
    );

    boolean existsByDonHang_MaDonHangAndLoaiGiaoDich(Integer maDonHang, String loaiGiaoDich);
}
