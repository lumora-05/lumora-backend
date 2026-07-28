package com.example.restaurant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetMailService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;
    private final String mailUsername;

    public PasswordResetMailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:}") String mailFrom,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
    }

    public void sendOtp(String recipient, String employeeName, String otp, long expirationMinutes) {
        if (!mailEnabled) {
            log.warn("MAIL_ENABLED=false - OTP đặt lại mật khẩu cho {} là {} (hết hạn sau {} phút)",
                    recipient, otp, expirationMinutes);
            return;
        }

        String sender = StringUtils.hasText(mailFrom) ? mailFrom.trim() : mailUsername.trim();
        if (!StringUtils.hasText(sender)) {
            throw new IllegalStateException("Chưa cấu hình địa chỉ email gửi thư");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(recipient);
        message.setSubject("Mã xác nhận đặt lại mật khẩu LUMORA");
        message.setText(buildBody(employeeName, otp, expirationMinutes));
        mailSender.send(message);
    }

    private String buildBody(String employeeName, String otp, long expirationMinutes) {
        String displayName = StringUtils.hasText(employeeName) ? employeeName.trim() : "bạn";
        return "Xin chào " + displayName + ",\n\n"
                + "Mã xác nhận đặt lại mật khẩu LUMORA của bạn là:\n\n"
                + otp + "\n\n"
                + "Mã có hiệu lực trong " + expirationMinutes + " phút và chỉ sử dụng được một lần.\n"
                + "Không cung cấp mã này cho bất kỳ ai.\n\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.\n\n"
                + "LUMORA Restaurant";
    }
}
