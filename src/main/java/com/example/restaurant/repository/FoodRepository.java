package com.example.restaurant.repository;

import com.example.restaurant.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Integer>, JpaSpecificationExecutor<Food> {
    interface CategoryFoodCount {
        Integer getMaDanhMuc();
        Long getSoMon();
    }

    List<Food> findByTrangThaiTrue();
    List<Food> findByDanhMuc_MaDanhMucAndTrangThaiTrue(Integer maDanhMuc);
    long countByTrangThaiTrue();

    @Query("""
            select f.danhMuc.maDanhMuc as maDanhMuc, count(f) as soMon
            from Food f
            where f.danhMuc.maDanhMuc in :categoryIds
            group by f.danhMuc.maDanhMuc
            """)
    List<CategoryFoodCount> countFoodsByCategoryIds(@Param("categoryIds") Collection<Integer> categoryIds);
}
