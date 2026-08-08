package com.example.restaurant.service;

import com.example.restaurant.entity.Employee;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tiện ích dùng chung để kiểm tra phạm vi khu vực của nhân viên phục vụ.
 *
 * <p>Hệ thống mới hỗ trợ một phục vụ phụ trách nhiều khu vực thông qua
 * {@code danhSachKhuVucPhuTrach}. Trường {@code khuVucPhuTrach} cũ vẫn được
 * đọc như phương án tương thích ngược để dữ liệu và frontend cũ tiếp tục hoạt động.</p>
 */
public final class WaiterAreaAccess {
    private static final String COMMON_AREA = "Khu vực chung";

    private WaiterAreaAccess() {
    }

    /** Danh sách khu vực đã chuẩn hóa khoảng trắng, giữ nguyên chữ để hiển thị. */
    public static Set<String> assignedAreas(Employee employee) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (employee == null) {
            return Set.of();
        }

        if (employee.getDanhSachKhuVucPhuTrach() != null) {
            employee.getDanhSachKhuVucPhuTrach().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(area -> unique.putIfAbsent(normalizeKey(area), area));
        }

        // Tương thích dữ liệu cũ: nếu bản ghi chưa được migrate sang bảng nhiều khu vực.
        if (StringUtils.hasText(employee.getKhuVucPhuTrach())) {
            String legacyArea = employee.getKhuVucPhuTrach().trim();
            unique.putIfAbsent(normalizeKey(legacyArea), legacyArea);
        }

        return new LinkedHashSet<>(unique.values());
    }

    /** Danh sách khu vực dạng chữ thường dùng cho truy vấn database. */
    public static Set<String> assignedAreaKeys(Employee employee) {
        Set<String> result = new LinkedHashSet<>();
        assignedAreas(employee).forEach(area -> result.add(normalizeKey(area)));
        return result;
    }

    public static boolean hasAssignedAreas(Employee employee) {
        return !assignedAreas(employee).isEmpty();
    }

    public static boolean canAccessArea(Employee employee, String area) {
        String expected = normalizeKey(normalizeTableArea(area));
        return assignedAreaKeys(employee).contains(expected);
    }

    public static String normalizeTableArea(String area) {
        return StringUtils.hasText(area) ? area.trim() : COMMON_AREA;
    }

    private static String normalizeKey(String value) {
        return normalizeTableArea(value).toLowerCase(Locale.ROOT);
    }
}
