package com.example.restaurant.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
public class GoogleTokenService {
    private static final String UNCONFIGURED_AUDIENCE = "google-client-id-not-configured";

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenService(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();

        try {
            this.verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(List.of(StringUtils.hasText(this.clientId)
                            ? this.clientId
                            : UNCONFIGURED_AUDIENCE))
                    .build();
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Không thể khởi tạo bộ xác minh Google ID token", exception);
        }
    }

    public GoogleIdToken.Payload verify(String credential) {
        if (!StringUtils.hasText(clientId)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Đăng nhập Google chưa được cấu hình trên máy chủ"
            );
        }

        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Thông tin đăng nhập Google không hợp lệ hoặc đã hết hạn"
                );
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!StringUtils.hasText(payload.getEmail())
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Email Google chưa được xác minh"
                );
            }

            return payload;
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không thể xác minh thông tin đăng nhập Google",
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối dịch vụ xác minh Google",
                    exception
            );
        }
    }
}
