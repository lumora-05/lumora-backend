package com.example.restaurant.service;

import com.example.restaurant.dto.ForgotPasswordResetRequest;
import com.example.restaurant.dto.ForgotPasswordSendCodeResponse;
import com.example.restaurant.dto.ForgotPasswordVerifyCodeRequest;
import com.example.restaurant.dto.ForgotPasswordVerifyCodeResponse;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.PasswordResetCode;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.PasswordResetCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class ForgotPasswordService {
    private static final String GENERIC_CODE_ERROR = "Mã xác nhận không đúng, đã hết hạn hoặc đã bị vô hiệu hóa";

    private final EmployeeRepository employeeRepository;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final PasswordResetMailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long otpExpirationMinutes;
    private final long resetTokenExpirationMinutes;
    private final long requestCooldownSeconds;
    private final int maxVerifyAttempts;

    public ForgotPasswordService(
            EmployeeRepository employeeRepository,
            PasswordResetCodeRepository resetCodeRepository,
            PasswordResetMailService mailService,
            PasswordEncoder passwordEncoder,
            @Value("${app.password-reset.otp-expiration-minutes:10}") long otpExpirationMinutes,
            @Value("${app.password-reset.reset-token-expiration-minutes:10}") long resetTokenExpirationMinutes,
            @Value("${app.password-reset.request-cooldown-seconds:60}") long requestCooldownSeconds,
            @Value("${app.password-reset.max-verify-attempts:5}") int maxVerifyAttempts) {
        this.employeeRepository = employeeRepository;
        this.resetCodeRepository = resetCodeRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.otpExpirationMinutes = Math.max(1, otpExpirationMinutes);
        this.resetTokenExpirationMinutes = Math.max(1, resetTokenExpirationMinutes);
        this.requestCooldownSeconds = Math.max(0, requestCooldownSeconds);
        this.maxVerifyAttempts = Math.max(1, maxVerifyAttempts);
    }

    @Transactional
    public ForgotPasswordSendCodeResponse sendCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Employee employee = employeeRepository.findByEmailIgnoreCaseForUpdate(email).orElse(null);

        // Luôn trả phản hồi chung ở controller để không làm lộ email có tồn tại hay không.
        if (employee == null || !"DANG_LAM_VIEC".equalsIgnoreCase(employee.getTrangThai())) {
            return sendCodeResponse();
        }

        LocalDateTime now = LocalDateTime.now();
        PasswordResetCode latest = resetCodeRepository.findTopByEmployeeOrderBySentAtDesc(employee).orElse(null);
        if (latest != null && latest.getSentAt() != null) {
            long elapsedSeconds = Duration.between(latest.getSentAt(), now).getSeconds();
            if (elapsedSeconds >= 0 && elapsedSeconds < requestCooldownSeconds) {
                return sendCodeResponse();
            }
        }

        resetCodeRepository.invalidateActiveByEmployeeId(employee.getMaNhanVien(), now);

        String otp = generateOtp();
        PasswordResetCode code = new PasswordResetCode();
        code.setEmployee(employee);
        code.setOtpHash(passwordEncoder.encode(otp));
        code.setOtpExpiresAt(now.plusMinutes(otpExpirationMinutes));
        code.setFailedAttempts(0);
        code.setSentAt(now);
        code.setCreatedAt(now);
        resetCodeRepository.save(code);

        mailService.sendOtp(employee.getEmail(), employee.getHoTen(), otp, otpExpirationMinutes);
        return sendCodeResponse();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ForgotPasswordVerifyCodeResponse verifyCode(ForgotPasswordVerifyCodeRequest request) {
        String email = normalizeEmail(request.email());
        PasswordResetCode code = resetCodeRepository.findTopByEmployee_EmailIgnoreCaseAndUsedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_CODE_ERROR));

        LocalDateTime now = LocalDateTime.now();
        validateOtpRecord(code, now);

        if (!passwordEncoder.matches(request.code(), code.getOtpHash())) {
            int attempts = code.getFailedAttempts() + 1;
            code.setFailedAttempts(attempts);
            if (attempts >= maxVerifyAttempts) {
                code.setUsedAt(now);
            }
            resetCodeRepository.save(code);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_CODE_ERROR);
        }

        String resetToken = generateResetToken();
        code.setVerifiedAt(now);
        code.setResetTokenHash(sha256(resetToken));
        code.setResetTokenExpiresAt(now.plusMinutes(resetTokenExpirationMinutes));
        resetCodeRepository.save(code);

        return new ForgotPasswordVerifyCodeResponse(
                resetToken,
                Duration.ofMinutes(resetTokenExpirationMinutes).toSeconds()
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void resetPassword(ForgotPasswordResetRequest request) {
        if (!request.matKhauMoi().equals(request.xacNhanMatKhauMoi())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới và xác nhận mật khẩu không khớp"
            );
        }

        String tokenHash = sha256(request.resetToken().trim());
        PasswordResetCode code = resetCodeRepository.findActiveByResetTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"
                ));

        LocalDateTime now = LocalDateTime.now();
        if (code.getVerifiedAt() == null
                || code.getResetTokenExpiresAt() == null
                || !code.getResetTokenExpiresAt().isAfter(now)) {
            code.setUsedAt(now);
            resetCodeRepository.save(code);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"
            );
        }

        Employee employee = code.getEmployee();
        if (employee == null || !"DANG_LAM_VIEC".equalsIgnoreCase(employee.getTrangThai())) {
            code.setUsedAt(now);
            resetCodeRepository.save(code);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể đặt lại mật khẩu cho tài khoản này");
        }
        if (passwordEncoder.matches(request.matKhauMoi(), employee.getMatKhau())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được giống mật khẩu hiện tại");
        }

        employee.setMatKhau(passwordEncoder.encode(request.matKhauMoi()));
        employeeRepository.save(employee);

        resetCodeRepository.invalidateOtherActiveByEmployeeId(
                employee.getMaNhanVien(),
                code.getId(),
                now
        );
        code.setUsedAt(now);
        resetCodeRepository.save(code);
    }

    private ForgotPasswordSendCodeResponse sendCodeResponse() {
        return new ForgotPasswordSendCodeResponse(
                Duration.ofMinutes(otpExpirationMinutes).toSeconds(),
                requestCooldownSeconds
        );
    }

    private void validateOtpRecord(PasswordResetCode code, LocalDateTime now) {
        if (code.getVerifiedAt() != null) {
            // OTP đã được xác minh, nhưng reset token đã cấp vẫn còn hiệu lực.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_CODE_ERROR);
        }
        if (code.getUsedAt() != null
                || code.getOtpExpiresAt() == null
                || !code.getOtpExpiresAt().isAfter(now)
                || code.getFailedAttempts() >= maxVerifyAttempts) {
            if (code.getUsedAt() == null) {
                code.setUsedAt(now);
                resetCodeRepository.save(code);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, GENERIC_CODE_ERROR);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOtp() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không thể tạo mã băm SHA-256", ex);
        }
    }
}
