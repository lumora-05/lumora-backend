package com.example.restaurant.repository;

import com.example.restaurant.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Integer> {
}
