package com.example.restaurant.service;

import com.example.restaurant.config.FirebasePushProperties;
import com.example.restaurant.entity.PushDeviceRegistration;
import com.example.restaurant.repository.PushDeviceRegistrationRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FirebasePushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    private final FirebasePushProperties properties;
    private final PushDeviceRegistrationRepository repository;
    private final ObjectProvider<FirebaseApp> firebaseAppProvider;

    public FirebasePushNotificationService(FirebasePushProperties properties,
                                           PushDeviceRegistrationRepository repository,
                                           ObjectProvider<FirebaseApp> firebaseAppProvider) {
        this.properties = properties;
        this.repository = repository;
        this.firebaseAppProvider = firebaseAppProvider;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled()) && firebaseAppProvider.getIfAvailable() != null;
    }

    /**
     * Gửi data-only message. Khi trang đang mở, WebSocket hiện tại tiếp tục đảm nhiệm cảnh báo.
     * Khi trang ở nền/đã đóng, service worker hiển thị push để tránh thông báo trùng trong foreground.
     */
    public void sendToChannel(String channel, String title, String body, String url, String tag, boolean urgent) {
        if (!isEnabled()) return;

        String normalizedChannel = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        List<String> fids = repository.findByChannelAndActiveTrue(normalizedChannel).stream()
                .map(PushDeviceRegistration::getFirebaseInstallationId)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(500)
                .toList();
        if (fids.isEmpty()) return;

        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", StringUtils.hasText(title) ? title : "LUMORA");
        data.put("body", StringUtils.hasText(body) ? body : "Có công việc mới đang chờ xử lý.");
        data.put("url", StringUtils.hasText(url) ? url : "/");
        data.put("tag", StringUtils.hasText(tag) ? tag : "lumora-staff-push");
        data.put("channel", normalizedChannel);
        data.put("urgent", Boolean.toString(urgent));

        MulticastMessage message = MulticastMessage.builder()
                .putAllData(data)
                .addAllFids(fids)
                .build();

        try {
            FirebaseApp app = firebaseAppProvider.getIfAvailable();
            if (app == null) return;
            BatchResponse response = FirebaseMessaging.getInstance(app).sendEachForMulticast(message);
            if (response.getFailureCount() > 0) {
                log.warn("FCM gửi kênh {}: thành công {}, thất bại {}", normalizedChannel,
                        response.getSuccessCount(), response.getFailureCount());
            }
        } catch (FirebaseMessagingException | RuntimeException ex) {
            // Push là kênh bổ trợ; lỗi FCM tuyệt đối không được rollback nghiệp vụ nhà hàng.
            log.error("Không thể gửi FCM push cho kênh {}", normalizedChannel, ex);
        }
    }
}
