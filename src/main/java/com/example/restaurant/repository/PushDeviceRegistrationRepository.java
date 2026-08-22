package com.example.restaurant.repository;

import com.example.restaurant.entity.PushDeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRegistrationRepository extends JpaRepository<PushDeviceRegistration, Long> {
    Optional<PushDeviceRegistration> findByFirebaseInstallationIdAndChannel(String firebaseInstallationId, String channel);

    List<PushDeviceRegistration> findByChannelAndActiveTrue(String channel);

    @Query("""
            select distinct d
            from PushDeviceRegistration d
            join fetch d.employee e
            join fetch e.vaiTro
            left join fetch e.danhSachKhuVucPhuTrach
            where d.channel = :channel
              and d.active = true
            """)
    List<PushDeviceRegistration> findActiveByChannelWithEmployee(@Param("channel") String channel);

    List<PushDeviceRegistration> findByChannelAndActiveTrueAndEmployee_MaNhanVien(
            String channel,
            Integer employeeId
    );

    void deleteByFirebaseInstallationIdAndChannelAndEmployee_MaNhanVien(
            String firebaseInstallationId,
            String channel,
            Integer employeeId
    );
}
