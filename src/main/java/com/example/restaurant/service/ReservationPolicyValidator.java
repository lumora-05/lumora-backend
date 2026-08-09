package com.example.restaurant.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Các quy tắc thời gian thuần cho nghiệp vụ đặt bàn. Tách riêng để dễ kiểm thử
 * và để frontend/backend có cùng ý nghĩa về các mốc thời gian.
 */
public final class ReservationPolicyValidator {
    private static final Pattern TIME_PATTERN = Pattern.compile("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)");

    private ReservationPolicyValidator() {
    }

    public static LocalDateTime bufferedStart(LocalDateTime start, int preparationMinutes) {
        return start.minusMinutes(Math.max(preparationMinutes, 0));
    }

    public static LocalDateTime bufferedEnd(LocalDateTime end, int preparationMinutes) {
        return end.plusMinutes(Math.max(preparationMinutes, 0));
    }

    public static boolean isWithinCheckInWindow(LocalDateTime now,
                                                LocalDateTime arrival,
                                                int checkInEarlyMinutes,
                                                int noShowGraceMinutes) {
        if (now == null || arrival == null) {
            return false;
        }
        LocalDateTime earliest = arrival.minusMinutes(Math.max(checkInEarlyMinutes, 0));
        LocalDateTime latest = arrival.plusMinutes(Math.max(noShowGraceMinutes, 0));
        return !now.isBefore(earliest) && !now.isAfter(latest);
    }

    public static boolean isWithinAdvanceWindow(LocalDateTime now,
                                                LocalDateTime arrival,
                                                int minimumAdvanceMinutes,
                                                int maximumAdvanceDays) {
        if (now == null || arrival == null) {
            return false;
        }
        LocalDateTime earliest = now.plusMinutes(Math.max(minimumAdvanceMinutes, 0));
        LocalDateTime latest = now.plusDays(Math.max(maximumAdvanceDays, 1));
        return !arrival.isBefore(earliest) && !arrival.isAfter(latest);
    }

    /**
     * Đọc hai mốc HH:mm đầu tiên từ chuỗi giờ mở cửa, ví dụ
     * "07:00 - 22:00 hằng ngày". Nếu không đọc được thì trả true để không làm
     * hỏng nghiệp vụ cũ; admin vẫn có thể sửa lại chuỗi cấu hình.
     */
    public static boolean isWithinOpeningHours(LocalDateTime arrival,
                                               LocalDateTime end,
                                               String openingHours) {
        if (arrival == null || end == null || openingHours == null || openingHours.isBlank()) {
            return true;
        }
        List<LocalTime> times = extractTimes(openingHours);
        if (times.size() < 2) {
            return true;
        }

        LocalTime open = times.get(0);
        LocalTime close = times.get(1);
        LocalDate date = arrival.toLocalDate();
        LocalDateTime opening;
        LocalDateTime closing;

        if (close.isAfter(open)) {
            opening = date.atTime(open);
            closing = date.atTime(close);
        } else {
            // Ca phục vụ qua nửa đêm, ví dụ 17:00 - 02:00.
            if (arrival.toLocalTime().isBefore(close)) {
                opening = date.minusDays(1).atTime(open);
                closing = date.atTime(close);
            } else {
                opening = date.atTime(open);
                closing = date.plusDays(1).atTime(close);
            }
        }
        return !arrival.isBefore(opening) && !end.isAfter(closing);
    }

    static List<LocalTime> extractTimes(String value) {
        List<LocalTime> result = new ArrayList<>();
        Matcher matcher = TIME_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find() && result.size() < 2) {
            result.add(LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        }
        return result;
    }
}
