package com.example.restaurant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.firebase.push")
public class FirebasePushProperties {
    /** Bật FCM Web Push. Mặc định tắt để backend vẫn chạy khi chưa cấu hình Firebase. */
    private Boolean enabled = false;
    /** Firebase/Google Cloud project id. */
    private String projectId = "";
    /** Service-account JSON nguyên bản. Chỉ truyền qua biến môi trường, không commit vào Git. */
    private String credentialsJson = "";
    /** Có thể dùng bản Base64 của service-account JSON thay cho credentialsJson. */
    private String credentialsBase64 = "";
    /** Nhắc lại các việc vận hành còn tồn đọng khi nhân viên không mở trang Lumora. */
    private Boolean remindersEnabled = true;
    private Long reminderIntervalMs = 60_000L;
}
