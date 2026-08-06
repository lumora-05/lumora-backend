package com.example.restaurant.repository;

import com.example.restaurant.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    List<OrderItem> findByTrangThaiMon(String trangThaiMon);

    List<OrderItem> findByTrangThaiMonAndSoLuongGreaterThanAndTrangThaiHuyIsNullOrderByMaChiTietAsc(
            String trangThaiMon,
            Integer soLuong
    );

    List<OrderItem> findByTrangThaiHuyOrderByThoiGianYeuCauHuyDesc(String trangThaiHuy);

    boolean existsByMonAn_MaMonAn(Integer maMonAn);

    @Query("""
            select i.monAn.maMonAn,
                   i.monAn.tenMonAn,
                   i.monAn.hinhAnh,
                   sum(i.soLuong),
                   sum(i.donGia * i.soLuong)
            from OrderItem i
            where exists (
                select inv.maHoaDon
                from Invoice inv
                where inv.donHang = i.donHang
                  and inv.trangThaiThanhToan = :paidStatus
                  and (
                      (inv.thoiGianThanhToan is not null and inv.thoiGianThanhToan between :from and :to)
                      or (inv.thoiGianThanhToan is null and inv.thoiGianTao between :from and :to)
                  )
            )
              and upper(i.trangThaiMon) <> 'DA_HUY'
            group by i.monAn.maMonAn, i.monAn.tenMonAn, i.monAn.hinhAnh
            order by sum(i.soLuong) desc
            """)
    List<Object[]> findTopFoods(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("paidStatus") String paidStatus);
}
