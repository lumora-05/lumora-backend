package com.example.restaurant.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationPolicyValidatorTest {

    @Test
    void reservationBufferRequiresPreparationGap() {
        LocalDateTime firstEnd = LocalDateTime.of(2026, 8, 9, 20, 0);
        LocalDateTime secondStartTooEarly = LocalDateTime.of(2026, 8, 9, 20, 20);
        LocalDateTime secondStartAllowed = LocalDateTime.of(2026, 8, 9, 20, 30);

        assertTrue(firstEnd.isAfter(ReservationPolicyValidator.bufferedStart(secondStartTooEarly, 30)));
        assertFalse(firstEnd.isAfter(ReservationPolicyValidator.bufferedStart(secondStartAllowed, 30)));
    }

    @Test
    void checkInOnlyWorksInsideConfiguredWindow() {
        LocalDateTime arrival = LocalDateTime.of(2026, 8, 9, 20, 0);
        assertFalse(ReservationPolicyValidator.isWithinCheckInWindow(
                LocalDateTime.of(2026, 8, 9, 19, 29), arrival, 30, 15));
        assertTrue(ReservationPolicyValidator.isWithinCheckInWindow(
                LocalDateTime.of(2026, 8, 9, 19, 30), arrival, 30, 15));
        assertTrue(ReservationPolicyValidator.isWithinCheckInWindow(
                LocalDateTime.of(2026, 8, 9, 20, 15), arrival, 30, 15));
        assertFalse(ReservationPolicyValidator.isWithinCheckInWindow(
                LocalDateTime.of(2026, 8, 9, 20, 16), arrival, 30, 15));
    }

    @Test
    void advanceWindowRejectsTooSoonAndTooFar() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 10, 0);
        assertFalse(ReservationPolicyValidator.isWithinAdvanceWindow(
                now, LocalDateTime.of(2026, 8, 9, 10, 29), 30, 60));
        assertTrue(ReservationPolicyValidator.isWithinAdvanceWindow(
                now, LocalDateTime.of(2026, 8, 9, 10, 30), 30, 60));
        assertFalse(ReservationPolicyValidator.isWithinAdvanceWindow(
                now, LocalDateTime.of(2026, 10, 9, 10, 1), 30, 60));
    }

    @Test
    void reservationMustFitInsideOpeningHours() {
        assertTrue(ReservationPolicyValidator.isWithinOpeningHours(
                LocalDateTime.of(2026, 8, 9, 19, 0),
                LocalDateTime.of(2026, 8, 9, 21, 0),
                "07:00 - 22:00 hằng ngày"));
        assertFalse(ReservationPolicyValidator.isWithinOpeningHours(
                LocalDateTime.of(2026, 8, 9, 21, 0),
                LocalDateTime.of(2026, 8, 9, 23, 0),
                "07:00 - 22:00 hằng ngày"));
    }

    @Test
    void overnightOpeningHoursAreSupported() {
        assertTrue(ReservationPolicyValidator.isWithinOpeningHours(
                LocalDateTime.of(2026, 8, 9, 23, 0),
                LocalDateTime.of(2026, 8, 10, 1, 0),
                "17:00 - 02:00"));
        assertFalse(ReservationPolicyValidator.isWithinOpeningHours(
                LocalDateTime.of(2026, 8, 10, 1, 30),
                LocalDateTime.of(2026, 8, 10, 2, 30),
                "17:00 - 02:00"));
    }
}
