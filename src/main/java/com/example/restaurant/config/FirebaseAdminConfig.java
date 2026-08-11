package com.example.restaurant.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseAdminConfig {

    @Bean
    @ConditionalOnProperty(name = "app.firebase.push.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(FirebasePushProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials;
        if (StringUtils.hasText(properties.getCredentialsJson())) {
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(
                    properties.getCredentialsJson().getBytes(StandardCharsets.UTF_8)
            ));
        } else if (StringUtils.hasText(properties.getCredentialsBase64())) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(properties.getCredentialsBase64().trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT_BASE64 không hợp lệ", ex);
            }
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        } else {
            // Cho phép dùng GOOGLE_APPLICATION_CREDENTIALS ở local/host hỗ trợ ADC.
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            options.setProjectId(properties.getProjectId().trim());
        }
        return FirebaseApp.initializeApp(options.build());
    }
}
