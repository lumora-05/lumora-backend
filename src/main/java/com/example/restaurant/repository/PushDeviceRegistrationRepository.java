package com.example.restaurant.repository;

import com.example.restaurant.entity.PushDeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRegistrationRepository extends JpaRepository<PushDeviceRegistration, Long> {
    Optional<PushDeviceRegistration> findByFirebaseInstallationIdAndChannel(String firebaseInstallationId, String channel);

    List<PushDeviceRegistration> findByChannelAndActiveTrue(String channel);

    void deleteByFirebaseInstallationIdAndChannelAndEmployee_MaNhanVien(
            String firebaseInstallationId,
            String channel,
            Integer employeeId
    );
}
