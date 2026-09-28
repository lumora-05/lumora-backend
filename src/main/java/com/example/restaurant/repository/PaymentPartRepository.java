package com.example.restaurant.repository;

import com.example.restaurant.entity.PaymentPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentPartRepository extends JpaRepository<PaymentPart, Long> {
    List<PaymentPart> findByDonHang_MaDonHangOrderByThoiGianThanhToanAscMaKhoanThanhToanAsc(Integer maDonHang);

    boolean existsByGiaoDichPayOs_MaGiaoDichPayOs(Long maGiaoDichPayOs);

    boolean existsByMaGiaoDichIgnoreCase(String maGiaoDich);
}
