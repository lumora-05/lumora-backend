package com.example.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Giới hạn số lần đăng nhập sai theo từng định danh tài khoản.
 *
 * Trạng thái khóa được giữ trong bộ nhớ để không làm thay đổi schema/database hiện tại.
 * Khi ứng dụng khởi động lại, bộ đếm được reset.
 */
@Service
public class LoginAttemptService {
    private static final int MAX_TRACKED_IDENTIFIERS = 10_000;

    private final int maxFailedAttempts;
    private final long lockDurationMillis;
    private final Map<String, AttemptState> attempts = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AttemptState> eldest) {
            return size() > MAX_TRACKED_IDENTIFIERS;
        }
    };

    public LoginAttemptService(
            @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login.lock-minutes:5}") long lockMinutes) {
        this.maxFailedAttempts = Math.max(1, maxFailedAttempts);
        this.lockDurationMillis = Duration.ofMinutes(Math.max(1, lockMinutes)).toMillis();
    }

    public synchronized void assertAllowed(String scope, String identifier) {
        String key = key(scope, identifier);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (state.lockedUntilMillis > now) {
            long remainingSeconds = Math.max(1, (state.lockedUntilMillis - now + 999) / 1000);
            long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau khoảng " + remainingMinutes + " phút"
            );
        }

        if (state.lockedUntilMillis > 0) {
            attempts.remove(key);
        }
    }

    public synchronized void recordFailure(String scope, String identifier) {
        String key = key(scope, identifier);
        long now = System.currentTimeMillis();
        AttemptState current = attempts.get(key);

        if (current == null || (current.lockedUntilMillis > 0 && current.lockedUntilMillis <= now)) {
            current = new AttemptState(0, 0);
        }

        int failedAttempts = current.failedAttempts + 1;
        long lockedUntil = failedAttempts >= maxFailedAttempts ? now + lockDurationMillis : 0;
        attempts.put(key, new AttemptState(failedAttempts, lockedUntil));
    }

    public synchronized void reset(String scope, String identifier) {
        attempts.remove(key(scope, identifier));
    }

    private String key(String scope, String identifier) {
        String normalizedScope = scope == null ? "LOGIN" : scope.trim().toUpperCase(Locale.ROOT);
        String normalizedIdentifier = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return normalizedScope + ':' + normalizedIdentifier;
    }

    private static final class AttemptState {
        private final int failedAttempts;
        private final long lockedUntilMillis;

        private AttemptState(int failedAttempts, long lockedUntilMillis) {
            this.failedAttempts = failedAttempts;
            this.lockedUntilMillis = lockedUntilMillis;
        }
    }
}
