package com.example.restaurant.repository;

import com.example.restaurant.entity.OrderItemBatchUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemBatchUsageRepository extends JpaRepository<OrderItemBatchUsage, Long> {
    boolean existsByChiTietDonHang_MaChiTiet(Integer itemId);

    @Query("""
            select u from OrderItemBatchUsage u
            join fetch u.nguyenLieu
            join fetch u.loNguyenLieu
            where u.chiTietDonHang.maChiTiet = :itemId
            order by u.nguyenLieu.tenNguyenLieu asc, u.loNguyenLieu.hanSuDung asc, u.maSuDung asc
            """)
    List<OrderItemBatchUsage> findTraceByOrderItemId(@Param("itemId") Integer itemId);

    @Query("""
            select u from OrderItemBatchUsage u
            join fetch u.nguyenLieu
            join fetch u.loNguyenLieu
            join fetch u.chiTietDonHang i
            join fetch i.monAn
            join fetch i.donHang o
            join fetch o.banAn
            where u.loNguyenLieu.maLo = :batchId
            order by u.thoiGianCapPhat desc, u.maSuDung desc
            """)
    List<OrderItemBatchUsage> findImpactByBatchId(@Param("batchId") Long batchId);
}
