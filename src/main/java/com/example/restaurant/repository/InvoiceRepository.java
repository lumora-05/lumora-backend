package com.example.restaurant.repository;

import com.example.restaurant.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {
    Optional<Invoice> findByDonHang_MaDonHang(Integer maDonHang);

    boolean existsByNhanVien_MaNhanVien(Integer maNhanVien);

    boolean existsByMaGiaoDichIgnoreCase(String maGiaoDich);

    /**
     * Đếm hóa đơn theo thời điểm thực tế thu tiền. Dữ liệu cũ chưa có
     * thoiGianThanhToan sẽ dùng thoiGianTao để vẫn được đưa vào báo cáo.
     */
    @Query("select count(i) from Invoice i " +
           "where i.trangThaiThanhToan = :status " +
           "and ((i.thoiGianThanhToan is not null and i.thoiGianThanhToan between :from and :to) " +
           "or (i.thoiGianThanhToan is null and i.thoiGianTao between :from and :to))")
    long countPaidInvoices(@Param("status") String status,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /** Tổng doanh thu theo thời điểm thanh toán thực tế. */
    @Query("select coalesce(sum(i.tongTien), 0) from Invoice i " +
           "where i.trangThaiThanhToan = :status " +
           "and ((i.thoiGianThanhToan is not null and i.thoiGianThanhToan between :from and :to) " +
           "or (i.thoiGianThanhToan is null and i.thoiGianTao between :from and :to))")
    BigDecimal totalRevenue(@Param("status") String status,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);
}
