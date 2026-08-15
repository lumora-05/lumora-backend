package com.example.restaurant.repository;

import com.example.restaurant.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {
    List<Order> findByTrangThai(String trangThai);

    List<Order> findByLoaiDonOrderByThoiGianDatDescMaDonHangDesc(String loaiDon);

    List<Order> findByKhachHang_MaKhachHangAndLoaiDonOrderByThoiGianDatDescMaDonHangDesc(
            Integer maKhachHang,
            String loaiDon
    );

    List<Order> findByLoaiDonAndTrangThaiOrderByThoiGianDatDescMaDonHangDesc(
            String loaiDon,
            String trangThai
    );
    @Query("""
            select o from Order o
            where lower(coalesce(o.banAn.khuVuc, 'Khu vực chung')) = lower(:khuVuc)
            order by o.thoiGianDat desc, o.maDonHang desc
            """)
    List<Order> findByBanAn_KhuVucIgnoreCaseOrderByThoiGianDatDescMaDonHangDesc(
            @Param("khuVuc") String khuVuc
    );

    @Query("""
            select o from Order o
            where lower(coalesce(o.banAn.khuVuc, 'Khu vực chung')) in :khuVuc
            order by o.thoiGianDat desc, o.maDonHang desc
            """)
    List<Order> findByBanAn_KhuVucInIgnoreCaseOrderByThoiGianDatDescMaDonHangDesc(
            @Param("khuVuc") Collection<String> khuVuc
    );

    @Query("""
            select o from Order o
            where upper(o.trangThai) = upper(:trangThai)
              and lower(coalesce(o.banAn.khuVuc, 'Khu vực chung')) = lower(:khuVuc)
            order by o.thoiGianDat desc, o.maDonHang desc
            """)
    List<Order> findByTrangThaiAndBanAn_KhuVucIgnoreCaseOrderByThoiGianDatDescMaDonHangDesc(
            @Param("trangThai") String trangThai,
            @Param("khuVuc") String khuVuc
    );

    @Query("""
            select o from Order o
            where upper(o.trangThai) = upper(:trangThai)
              and lower(coalesce(o.banAn.khuVuc, 'Khu vực chung')) in :khuVuc
            order by o.thoiGianDat desc, o.maDonHang desc
            """)
    List<Order> findByTrangThaiAndBanAn_KhuVucInIgnoreCaseOrderByThoiGianDatDescMaDonHangDesc(
            @Param("trangThai") String trangThai,
            @Param("khuVuc") Collection<String> khuVuc
    );
    List<Order> findByBanAn_MaBan(Integer maBan);
    List<Order> findByThoiGianDatBetween(LocalDateTime from, LocalDateTime to);
    List<Order> findAllByOrderByThoiGianDatDesc(Pageable pageable);
    long countByTrangThai(String trangThai);
    long countByThoiGianDatBetween(LocalDateTime from, LocalDateTime to);
    long countByTrangThaiAndThoiGianDatBetween(String trangThai, LocalDateTime from, LocalDateTime to);
    boolean existsByNhanVien_MaNhanVien(Integer maNhanVien);

    List<Order> findByBanAn_MaBanAndTrangThaiInOrderByThoiGianDatDescMaDonHangDesc(
            Integer maBan,
            Collection<String> trangThai
    );

    boolean existsByBanAn_MaBanAndTrangThaiInAndMaDonHangNot(
            Integer maBan,
            Collection<String> trangThai,
            Integer maDonHang
    );

    @Query("""
            select o from Order o
            where o.banAn.maBan = :tableId
              and o.trangThai in :statuses
            order by o.thoiGianDat desc, o.maDonHang desc
            """)
    List<Order> findOpenOrders(@Param("tableId") Integer tableId,
                               @Param("statuses") Collection<String> statuses,
                               Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.maDonHang = :orderId")
    java.util.Optional<Order> findByIdForUpdate(@Param("orderId") Integer orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.banAn.maBan = :tableId
              and o.trangThai in :statuses
            order by o.thoiGianDat desc, o.maDonHang desc
            """)
    List<Order> findOpenOrdersForUpdate(@Param("tableId") Integer tableId,
                                         @Param("statuses") Collection<String> statuses,
                                         Pageable pageable);


    /**
     * Khóa đơn hàng chứa chi tiết món đang được cập nhật. Việc này giúp các request
     * xác nhận nhiều món cùng lúc (frontend dùng Promise.all) được xử lý tuần tự,
     * bảo đảm request cuối cùng có thể chuyển toàn đơn sang DA_PHUC_VU.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            join o.chiTietDonHang i
            where i.maChiTiet = :itemId
            """)
    java.util.Optional<Order> findOrderByItemIdForUpdate(@Param("itemId") Integer itemId);

    @Query("select count(distinct o) from Order o join o.chiTietDonHang i where i.trangThaiMon = :status")
    long countOrderHasItemStatus(@Param("status") String status);

    @Query("select o.trangThai, count(o) from Order o group by o.trangThai order by count(o) desc")
    List<Object[]> countByTrangThaiGroup();
}
