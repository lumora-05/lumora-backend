package com.example.restaurant.repository;

import com.example.restaurant.entity.IngredientBatchIncident;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientBatchIncidentRepository extends JpaRepository<IngredientBatchIncident, Long> {
    List<IngredientBatchIncident> findAllByOrderByThoiGianPhatHienDescMaSuCoDesc();

    List<IngredientBatchIncident> findByLoNguyenLieu_MaLoOrderByThoiGianPhatHienDescMaSuCoDesc(Long batchId);

    @Query("""
            select case when count(i) > 0 then true else false end
            from IngredientBatchIncident i
            where i.loNguyenLieu.maLo = :batchId
              and i.trangThai in ('MOI', 'DANG_XU_LY')
            """)
    boolean existsOpenIncidentByBatchId(@Param("batchId") Long batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IngredientBatchIncident i join fetch i.loNguyenLieu where i.maSuCo = :id")
    Optional<IngredientBatchIncident> findByIdForUpdate(@Param("id") Long id);
}
