package com.example.restaurant.repository;

import com.example.restaurant.entity.AdminNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long>,
        JpaSpecificationExecutor<AdminNotification> {

    long countByDaDocFalse();

    Optional<AdminNotification> findFirstByMaNguyenLieuAndTrangThaiTonKhoAndDaDocFalseOrderByThoiGianTaoDesc(
            Integer maNguyenLieu,
            String trangThaiTonKho
    );

    List<AdminNotification> findAllByMaNguyenLieuAndDaDocFalse(Integer maNguyenLieu);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AdminNotification n
               set n.daDoc = true,
                   n.thoiGianDoc = :readAt
             where n.daDoc = false
            """)
    int markAllAsRead(@Param("readAt") LocalDateTime readAt);
}
