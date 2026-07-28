package com.example.restaurant.repository;

import com.example.restaurant.entity.DiningTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Integer> {
    List<DiningTable> findAllByOrderByMaBanAsc();

    @Query("""
            select t from DiningTable t
            where lower(coalesce(t.khuVuc, 'Khu vực chung')) = lower(:khuVuc)
            order by t.maBan asc
            """)
    List<DiningTable> findByKhuVucIgnoreCaseOrderByMaBanAsc(@Param("khuVuc") String khuVuc);

    List<DiningTable> findByTrangThai(String trangThai);

    long countByTrangThai(String trangThai);

    boolean existsByTenBanIgnoreCase(String tenBan);

    boolean existsByTenBanIgnoreCaseAndMaBanNot(String tenBan, Integer maBan);

    Optional<DiningTable> findByQrToken(String qrToken);

    boolean existsByQrToken(String qrToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.maBan = :maBan")
    Optional<DiningTable> findByIdForUpdate(@Param("maBan") Integer maBan);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.qrToken = :qrToken")
    Optional<DiningTable> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

    List<DiningTable> findByMaNhomBanOrderByMaBanAsc(String maNhomBan);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.maBan in :ids order by t.maBan asc")
    List<DiningTable> findAllByIdsForUpdate(@Param("ids") List<Integer> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTable t where t.maNhomBan = :groupId order by t.maBan asc")
    List<DiningTable> findByMaNhomBanForUpdate(@Param("groupId") String groupId);
}
