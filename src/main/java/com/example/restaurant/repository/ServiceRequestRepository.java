package com.example.restaurant.repository;

import com.example.restaurant.entity.ServiceRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Integer> {
    List<ServiceRequest> findAllByOrderByThoiGianTaoDesc();

    List<ServiceRequest> findByTrangThaiOrderByThoiGianTaoDesc(String trangThai);

    List<ServiceRequest> findByTrangThaiInOrderByThoiGianTaoAsc(Collection<String> trangThai);

    List<ServiceRequest> findByKhuVucIgnoreCaseOrderByThoiGianTaoDesc(String khuVuc);

    List<ServiceRequest> findByKhuVucIgnoreCaseAndTrangThaiOrderByThoiGianTaoDesc(
            String khuVuc,
            String trangThai
    );

    List<ServiceRequest> findByKhuVucIgnoreCaseAndTrangThaiInOrderByThoiGianTaoAsc(
            String khuVuc,
            Collection<String> trangThai
    );

    List<ServiceRequest> findByMaBanAndTrangThaiInOrderByThoiGianTaoDesc(
            Integer maBan,
            Collection<String> trangThai
    );

    List<ServiceRequest> findTop20ByMaBanOrderByThoiGianTaoDesc(Integer maBan);

    List<ServiceRequest> findByMaBanInAndTrangThaiIn(
            Collection<Integer> maBan,
            Collection<String> trangThai
    );

    boolean existsByMaBanAndLoaiYeuCauAndTrangThaiIn(
            Integer maBan,
            String loaiYeuCau,
            Collection<String> trangThai
    );

    long countByMaBanAndTrangThaiIn(Integer maBan, Collection<String> trangThai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.maYeuCau = :id")
    Optional<ServiceRequest> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from ServiceRequest r
            where r.maBan = :maBan and r.trangThai in :statuses
            order by r.thoiGianTao asc
            """)
    List<ServiceRequest> findOpenByTableForUpdate(
            @Param("maBan") Integer maBan,
            @Param("statuses") Collection<String> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from ServiceRequest r
            where r.maBan in :tableIds and r.trangThai in :statuses
            order by r.thoiGianTao asc
            """)
    List<ServiceRequest> findOpenByTablesForUpdate(
            @Param("tableIds") Collection<Integer> tableIds,
            @Param("statuses") Collection<String> statuses
    );
}
