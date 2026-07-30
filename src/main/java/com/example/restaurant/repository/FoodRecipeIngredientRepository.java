package com.example.restaurant.repository;

import com.example.restaurant.entity.FoodRecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodRecipeIngredientRepository extends JpaRepository<FoodRecipeIngredient, Long> {
    @Query("""
            select r from FoodRecipeIngredient r
            join fetch r.nguyenLieu
            where r.monAn.maMonAn = :foodId
            order by r.nguyenLieu.tenNguyenLieu asc, r.maCongThuc asc
            """)
    List<FoodRecipeIngredient> findAllByFoodId(@Param("foodId") Integer foodId);

    @Query("""
            select r from FoodRecipeIngredient r
            join fetch r.nguyenLieu
            where r.monAn.maMonAn = :foodId
              and r.trangThai = true
            order by r.nguyenLieu.maNguyenLieu asc, r.maCongThuc asc
            """)
    List<FoodRecipeIngredient> findActiveByFoodId(@Param("foodId") Integer foodId);

    void deleteByMonAn_MaMonAn(Integer foodId);

    boolean existsByMonAn_MaMonAnAndTrangThaiTrue(Integer foodId);
}
