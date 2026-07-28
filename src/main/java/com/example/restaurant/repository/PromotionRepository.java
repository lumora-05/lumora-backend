package com.example.restaurant.repository;

import com.example.restaurant.entity.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Integer>, JpaSpecificationExecutor<Promotion> {
    Optional<Promotion> findByMaCodeAndTrangThaiTrue(String maCode);
    Optional<Promotion> findByMaCode(String maCode);
    Optional<Promotion> findByMaCodeIgnoreCase(String maCode);

    List<Promotion> findByTrangThaiTrueAndNgayBatDauLessThanEqualAndNgayKetThucGreaterThanEqual(
            LocalDate from,
            LocalDate to
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where upper(p.maCode) = upper(:maCode) and p.trangThai = true")
    Optional<Promotion> findActiveByCodeForUpdate(@Param("maCode") String maCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.maKhuyenMai = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Integer id);
}
