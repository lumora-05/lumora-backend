package com.example.restaurant.repository;

import com.example.restaurant.entity.OrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, Long> {
    Optional<OrderDelivery> findByTrackingToken(String trackingToken);

    Optional<OrderDelivery> findByClientRequestId(String clientRequestId);

    boolean existsByMaVanChuyen(String maVanChuyen);

    Optional<OrderDelivery> findByMaVanChuyen(String maVanChuyen);

    boolean existsByMaGiaoDichIgnoreCase(String maGiaoDich);
}
