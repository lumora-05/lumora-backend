package com.example.restaurant.service;

import com.example.restaurant.config.DeliveryProperties;
import com.example.restaurant.dto.DeliveryOtpRequestResponse;
import com.example.restaurant.dto.DeliveryOtpVerifyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xác thực số điện thoại trước khi tạo đơn giao hàng công khai.
 * Bản đồ án dùng OTP demo để không phụ thuộc nhà cung cấp SMS; khi triển khai thật
 * có thể thay bước phát OTP bằng adapter SMS mà không đổi luồng đặt món.
 */
@Service
public class DeliveryPhoneVerificationService {
    private static final long REQUEST_COOLDOWN_SECONDS = 45L;

    private final DeliveryProperties deliveryProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, VerifiedPhone> verifiedTokens = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastRequestByPhone = new ConcurrentHashMap<>();

    public DeliveryPhoneVerificationService(DeliveryProperties deliveryProperties) {
        this.deliveryProperties = deliveryProperties;
    }

    public DeliveryOtpRequestResponse requestOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        pruneExpired();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastRequest = lastRequestByPhone.get(phone);
        if (lastRequest != null && lastRequest.plusSeconds(REQUEST_COOLDOWN_SECONDS).isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Vui lòng chờ ít phút trước khi yêu cầu mã OTP mới"
            );
        }

        int expiryMinutes = positiveOrDefault(deliveryProperties.getOtpExpiryMinutes(), 5);
        String requestId = UUID.randomUUID().toString();
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        LocalDateTime expiresAt = now.plusMinutes(expiryMinutes);
        challenges.put(requestId, new OtpChallenge(phone, otp, expiresAt, 0));
        lastRequestByPhone.put(phone, now);

        boolean exposeDemoOtp = Boolean.TRUE.equals(deliveryProperties.getExposeDemoOtp());
        if (!exposeDemoOtp) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình nhà cung cấp SMS. Hãy bật OTP demo hoặc tích hợp dịch vụ SMS"
            );
        }
        return new DeliveryOtpRequestResponse(requestId, expiresAt, otp);
    }

    public DeliveryOtpVerifyResponse verifyOtp(String requestId, String rawPhone, String otp) {
        String normalizedRequestId = requiredText(requestId, "Mã yêu cầu OTP không hợp lệ");
        String phone = normalizePhone(rawPhone);
        String code = requiredText(otp, "OTP không hợp lệ");
        if (!code.matches("^[0-9]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP phải gồm 6 chữ số");
        }

        pruneExpired();
        OtpChallenge challenge = challenges.get(normalizedRequestId);
        if (challenge == null || !challenge.phone().equals(phone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yêu cầu OTP không tồn tại hoặc đã hết hạn");
        }
        if (challenge.expiresAt().isBefore(LocalDateTime.now())) {
            challenges.remove(normalizedRequestId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn");
        }
        if (challenge.attempts() >= 5) {
            challenges.remove(normalizedRequestId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai OTP quá số lần cho phép");
        }
        if (!challenge.otp().equals(code)) {
            challenges.put(
                    normalizedRequestId,
                    new OtpChallenge(challenge.phone(), challenge.otp(), challenge.expiresAt(), challenge.attempts() + 1)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP không đúng");
        }

        challenges.remove(normalizedRequestId);
        int verificationMinutes = positiveOrDefault(deliveryProperties.getPhoneVerificationMinutes(), 20);
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(verificationMinutes);
        verifiedTokens.put(token, new VerifiedPhone(phone, expiresAt));
        return new DeliveryOtpVerifyResponse(token, expiresAt);
    }

    public void consumeVerifiedPhone(String verificationToken, String rawPhone) {
        if (!Boolean.TRUE.equals(deliveryProperties.getRequirePhoneVerification())) {
            return;
        }
        String token = requiredText(verificationToken, "Vui lòng xác thực số điện thoại trước khi đặt hàng");
        String phone = normalizePhone(rawPhone);
        pruneExpired();
        VerifiedPhone verified = verifiedTokens.get(token);
        if (verified == null || !verified.phone().equals(phone) || verified.expiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Xác thực số điện thoại đã hết hạn hoặc không khớp. Vui lòng xác thực lại"
            );
        }
        verifiedTokens.remove(token);
    }

    private void pruneExpired() {
        LocalDateTime now = LocalDateTime.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        verifiedTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        lastRequestByPhone.entrySet().removeIf(entry -> entry.getValue().plusHours(1).isBefore(now));
    }

    private String normalizePhone(String value) {
        String phone = requiredText(value, "Số điện thoại không hợp lệ").replaceAll("[ .()\\-]", "");
        if (!phone.matches("^\\+?[0-9]{9,15}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        return phone;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private record OtpChallenge(String phone, String otp, LocalDateTime expiresAt, int attempts) {
    }

    private record VerifiedPhone(String phone, LocalDateTime expiresAt) {
    }
}
