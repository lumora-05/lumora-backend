package com.example.restaurant.repository;

import com.example.restaurant.entity.DeliveryRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRefundRepository extends JpaRepository<DeliveryRefund, Long> {
    boolean existsByMaGiaoDichIgnoreCase(String maGiaoDich);
}
