package com.example.restaurant.repository;

import com.example.restaurant.entity.TableReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TableReservationRepository extends JpaRepository<TableReservation, Integer>, JpaSpecificationExecutor<TableReservation> {
    Optional<TableReservation> findByMaTraCuuIgnoreCase(String maTraCuu);
    boolean existsByMaTraCuuIgnoreCase(String maTraCuu);
    List<TableReservation> findBySoDienThoaiOrderByNgayGioDenDescMaDatBanDesc(String soDienThoai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TableReservation r where r.maDatBan = :id")
    Optional<TableReservation> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TableReservation r where upper(r.maTraCuu) = upper(:code)")
    Optional<TableReservation> findByCodeForUpdate(@Param("code") String code);

    @Query("""
            select count(r) from TableReservation r
            where r.trangThai in :statuses
              and r.maDatBan <> :excludeId
              and (
                    (r.banThucTe is not null and r.banThucTe.maBan = :tableId)
                    or
                    (r.banThucTe is null and r.banDuKien.maBan = :tableId)
              )
              and r.ngayGioDen < :endTime
              and r.thoiGianKetThucDuKien > :startTime
            """)
    long countOverlappingForTable(@Param("tableId") Integer tableId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("statuses") Collection<String> statuses,
                                  @Param("excludeId") Integer excludeId);


    @Query("""
            select r from TableReservation r
            where r.trangThai in :statuses
              and (
                    (r.banThucTe is not null and r.banThucTe.maBan = :tableId)
                    or
                    (r.banThucTe is null and r.banDuKien.maBan = :tableId)
              )
              and r.ngayGioDen < :serviceEndWithPreparation
              and r.thoiGianKetThucDuKien > :serviceStart
            order by r.ngayGioDen asc, r.maDatBan asc
            """)
    List<TableReservation> findConflictingReservationsForNewService(
            @Param("tableId") Integer tableId,
            @Param("serviceStart") LocalDateTime serviceStart,
            @Param("serviceEndWithPreparation") LocalDateTime serviceEndWithPreparation,
            @Param("statuses") Collection<String> statuses);


    @Query("""
            select count(r) from TableReservation r
            where r.trangThai in :statuses
              and r.maDatBan <> :excludeId
              and r.soDienThoai = :phone
              and r.ngayGioDen < :endTime
              and r.thoiGianKetThucDuKien > :startTime
            """)
    long countOverlappingForCustomer(@Param("phone") String phone,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("statuses") Collection<String> statuses,
                                     @Param("excludeId") Integer excludeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from TableReservation r
            where r.trangThai = :status
              and r.ngayGioDen <= :deadline
            order by r.ngayGioDen asc, r.maDatBan asc
            """)
    List<TableReservation> findOverdueByStatusForUpdate(@Param("status") String status,
                                                         @Param("deadline") LocalDateTime deadline);

    @Query("""
            select count(r) from TableReservation r
            where r.trangThai in :statuses
              and (
                    (r.banThucTe is not null and r.banThucTe.maBan = :tableId)
                    or
                    (r.banThucTe is null and r.banDuKien.maBan = :tableId)
              )
              and r.thoiGianKetThucDuKien > :now
            """)
    long countFutureOrActiveForTable(@Param("tableId") Integer tableId,
                                     @Param("now") LocalDateTime now,
                                     @Param("statuses") Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from TableReservation r
            where r.banThucTe.maBan = :tableId
              and r.trangThai = :status
              and r.donHang is null
            order by r.thoiGianXepBan desc, r.maDatBan desc
            """)
    List<TableReservation> findAssignedWithoutOrderForUpdate(@Param("tableId") Integer tableId,
                                                              @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TableReservation r where r.donHang.maDonHang = :orderId")
    Optional<TableReservation> findByOrderIdForUpdate(@Param("orderId") Integer orderId);
}
