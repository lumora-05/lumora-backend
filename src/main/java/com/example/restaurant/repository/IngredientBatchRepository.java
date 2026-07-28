package com.example.restaurant.repository;

import com.example.restaurant.entity.IngredientBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IngredientBatchRepository
        extends JpaRepository<IngredientBatch, Long>, JpaSpecificationExecutor<IngredientBatch> {

    boolean existsByNguyenLieuMaNguyenLieuAndSoLoIgnoreCase(Integer ingredientId, String soLo);

    boolean existsByNguyenLieuMaNguyenLieuAndSoLoIgnoreCaseAndMaLoNot(
            Integer ingredientId, String soLo, Long maLo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from IngredientBatch b join fetch b.nguyenLieu where b.maLo = :id")
    Optional<IngredientBatch> findByIdForUpdate(@Param("id") Long id);

    @Query("select b from IngredientBatch b where b.nguyenLieu.maNguyenLieu = :ingredientId "
            + "order by case when b.hanSuDung is null then 1 else 0 end, "
            + "b.hanSuDung asc, b.ngayNhap asc, b.maLo asc")
    List<IngredientBatch> findAllByIngredientForFefo(@Param("ingredientId") Integer ingredientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from IngredientBatch b where b.nguyenLieu.maNguyenLieu = :ingredientId "
            + "and b.trangThai = true and b.soLuongConLai > 0 "
            + "order by case when b.hanSuDung is null then 1 else 0 end, "
            + "b.hanSuDung asc, b.ngayNhap asc, b.maLo asc")
    List<IngredientBatch> findAvailableByIngredientForUpdate(@Param("ingredientId") Integer ingredientId);

    @Query("select coalesce(sum(b.soLuongConLai), 0) from IngredientBatch b "
            + "where b.nguyenLieu.maNguyenLieu = :ingredientId")
    BigDecimal sumRemainingByIngredient(@Param("ingredientId") Integer ingredientId);

    @Query("select coalesce(sum(b.soLuongConLai), 0) from IngredientBatch b "
            + "where b.nguyenLieu.maNguyenLieu = :ingredientId "
            + "and b.trangThai = true and b.soLuongConLai > 0 "
            + "and (b.hanSuDung is null or b.hanSuDung >= :today)")
    BigDecimal sumUsableRemainingByIngredient(@Param("ingredientId") Integer ingredientId,
                                              @Param("today") LocalDate today);

    @Query("select coalesce(sum(b.soLuongConLai), 0) from IngredientBatch b "
            + "where b.nguyenLieu.maNguyenLieu = :ingredientId "
            + "and b.soLuongConLai > 0 and b.hanSuDung < :today")
    BigDecimal sumExpiredRemainingByIngredient(@Param("ingredientId") Integer ingredientId,
                                               @Param("today") LocalDate today);
}
