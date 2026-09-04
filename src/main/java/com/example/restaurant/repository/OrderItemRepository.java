package com.example.restaurant.repository;

import com.example.restaurant.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    List<OrderItem> findByTrangThaiMon(String trangThaiMon);

    List<OrderItem> findByTrangThaiMonAndSoLuongGreaterThanAndTrangThaiHuyIsNullOrderByMaChiTietAsc(
            String trangThaiMon,
            Integer soLuong
    );

    long countByTrangThaiMonAndSoLuongGreaterThanAndTrangThaiHuyIsNull(
            String trangThaiMon,
            Integer soLuong
    );

    long countByTrangThaiMonInAndSoLuongGreaterThanAndTrangThaiHuyIsNull(
            java.util.Collection<String> trangThaiMon,
            Integer soLuong
    );

    List<OrderItem> findByTrangThaiHuyOrderByThoiGianYeuCauHuyDesc(String trangThaiHuy);

    @Query("select distinct i.donHang.maDonHang from OrderItem i where i.maChiTiet in :itemIds")
    List<Integer> findDistinctOrderIdsByItemIds(@Param("itemIds") Collection<Integer> itemIds);

    @Query("""
            select coalesce(sum(i.soLuong), 0)
            from OrderItem i
            where upper(i.trangThaiMon) in :readyStatuses
              and i.soLuong > 0
              and (i.trangThaiHuy is null or upper(i.trangThaiHuy) = 'TU_CHOI')
              and i.donHang.banAn is not null
              and upper(coalesce(i.donHang.loaiDon, 'TAI_BAN')) <> 'GIAO_HANG'
              and upper(coalesce(i.donHang.trangThai, '')) not in :excludedOrderStatuses
              and lower(coalesce(i.donHang.banAn.khuVuc, 'Khu vực chung')) in :areaKeys
            """)
    Long sumReadyQuantityForWaiterAreas(@Param("readyStatuses") java.util.Collection<String> readyStatuses,
                                        @Param("excludedOrderStatuses") java.util.Collection<String> excludedOrderStatuses,
                                        @Param("areaKeys") java.util.Collection<String> areaKeys);

    boolean existsByMonAn_MaMonAn(Integer maMonAn);

    @Query("""
            select i.monAn.maMonAn,
                   i.monAn.tenMonAn,
                   i.monAn.tenMonAnEn,
                   i.monAn.gia,
                   i.monAn.moTa,
                   i.monAn.moTaEn,
                   i.monAn.hinhAnh,
                   sum(i.soLuong)
            from OrderItem i
            where i.monAn.trangThai = true
              and exists (
                  select inv.maHoaDon
                  from Invoice inv
                  where inv.donHang = i.donHang
                    and inv.trangThaiThanhToan = :paidStatus
              )
              and upper(i.trangThaiMon) <> 'DA_HUY'
            group by i.monAn.maMonAn,
                     i.monAn.tenMonAn,
                     i.monAn.tenMonAnEn,
                     i.monAn.gia,
                     i.monAn.moTa,
                     i.monAn.moTaEn,
                     i.monAn.hinhAnh
            order by sum(i.soLuong) desc, i.monAn.maMonAn asc
            """)
    List<Object[]> findTopSellingFoods(@Param("paidStatus") String paidStatus);

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
