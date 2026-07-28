package com.example.restaurant.repository;

import com.example.restaurant.entity.Ingredient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Integer>, JpaSpecificationExecutor<Ingredient> {
    boolean existsByTenNguyenLieuIgnoreCase(String tenNguyenLieu);

    boolean existsByTenNguyenLieuIgnoreCaseAndMaNguyenLieuNot(String tenNguyenLieu, Integer maNguyenLieu);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.maNguyenLieu = :id")
    Optional<Ingredient> findByIdForUpdate(@Param("id") Integer id);

    @Query("select i from Ingredient i where i.trangThai = true and i.soLuongTon <= i.mucTonToiThieu")
    List<Ingredient> findLowStock(Sort sort);
}
