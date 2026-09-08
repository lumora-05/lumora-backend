package com.example.restaurant.repository;

import com.example.restaurant.entity.ReservationPayOsPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationPayOsPaymentRepository extends JpaRepository<ReservationPayOsPayment, Long> {
    boolean existsByPayOsOrderCode(Long payOsOrderCode);

    boolean existsByMaThamChieuIgnoreCase(String maThamChieu);

    List<ReservationPayOsPayment> findByDatBan_MaDatBanAndTrangThaiOrderByThoiGianTaoDesc(
            Integer maDatBan,
            String trangThai
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ReservationPayOsPayment p where p.payOsOrderCode = :orderCode")
    Optional<ReservationPayOsPayment> findByPayOsOrderCodeForUpdate(@Param("orderCode") Long orderCode);
}
