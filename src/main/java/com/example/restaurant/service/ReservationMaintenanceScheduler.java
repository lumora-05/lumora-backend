package com.example.restaurant.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationMaintenanceScheduler {
    private final ReservationService reservationService;

    public ReservationMaintenanceScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Kiểm tra mỗi phút để đóng các lịch đã quá thời gian giữ chỗ. */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void expireOverdueReservations() {
        reservationService.expireOverdueReservations();
    }
}
