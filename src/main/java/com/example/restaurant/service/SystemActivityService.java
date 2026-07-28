package com.example.restaurant.service;

import com.example.restaurant.entity.SystemActivity;
import com.example.restaurant.repository.SystemActivityRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemActivityService {
    private final SystemActivityRepository systemActivityRepository;

    public SystemActivityService(SystemActivityRepository systemActivityRepository) {
        this.systemActivityRepository = systemActivityRepository;
    }

    @Transactional
    public SystemActivity record(String type, String content, Integer targetId) {
        SystemActivity activity = new SystemActivity();
        activity.setLoaiHoatDong(type);
        activity.setNoiDung(content);
        activity.setDoiTuongId(targetId);
        activity.setNguoiThucHien(resolveActor());
        return systemActivityRepository.save(activity);
    }

    private String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return "Khách hàng";
        }
        return authentication.getName();
    }
}
