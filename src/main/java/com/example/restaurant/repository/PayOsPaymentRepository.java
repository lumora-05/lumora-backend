package com.example.restaurant.repository;

import com.example.restaurant.entity.PayOsPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayOsPaymentRepository extends JpaRepository<PayOsPayment, Long> {
    boolean existsByPayOsOrderCode(Long payOsOrderCode);

    boolean existsByMaThamChieuIgnoreCase(String maThamChieu);

    List<PayOsPayment> findByDonHang_MaDonHangAndTrangThaiOrderByThoiGianTaoDesc(
            Integer maDonHang,
            String trangThai
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayOsPayment p where p.payOsOrderCode = :orderCode")
    Optional<PayOsPayment> findByPayOsOrderCodeForUpdate(@Param("orderCode") Long orderCode);
}
