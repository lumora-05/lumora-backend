package com.example.restaurant.repository;

import com.example.restaurant.entity.SystemActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemActivityRepository extends JpaRepository<SystemActivity, Long> {
    List<SystemActivity> findAllByOrderByThoiGianDesc(Pageable pageable);
}
