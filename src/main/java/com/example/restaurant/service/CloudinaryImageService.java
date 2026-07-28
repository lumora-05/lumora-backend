package com.example.restaurant.service;

import com.cloudinary.Cloudinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryImageService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageService.class);
    private static final String UPLOAD_PATH_MARKER = "/image/upload/";

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public CloudinaryImageService(
            Cloudinary cloudinary,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String upload(byte[] content, String folder, String publicIdPrefix) {
        return upload(content, folder, publicIdPrefix, null);
    }

    public String upload(byte[] content, String folder, String publicIdPrefix, String format) {
        ensureConfigured();
        if (content == null || content.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu ảnh không được bỏ trống");
        }

        String normalizedFolder = normalizeFolder(folder);
        String normalizedPrefix = normalizePublicIdPart(publicIdPrefix, "image");
        String publicId = normalizedFolder + "/" + normalizedPrefix + "-" + UUID.randomUUID();

        Map<String, Object> options = new HashMap<>();
        options.put("public_id", publicId);
        options.put("resource_type", "image");
        options.put("overwrite", false);
        if (format != null && !format.isBlank()) {
            options.put("format", format.trim().toLowerCase());
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(content, options);
            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null || secureUrl.toString().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Cloudinary không trả về đường dẫn ảnh"
                );
            }
            return secureUrl.toString();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể tải ảnh lên Cloudinary",
                    ex
            );
        }
    }

    /**
     * Xóa ảnh Cloudinary theo URL. Lỗi dọn ảnh chỉ được ghi log để không làm hỏng
     * nghiệp vụ chính đã hoàn tất trong database.
     */
    public void deleteByUrl(String imageUrl) {
        if (!isCloudinaryUrl(imageUrl)) {
            return;
        }

        String publicId = extractPublicId(imageUrl);
        if (publicId == null || publicId.isBlank()) {
            log.warn("Không xác định được public_id Cloudinary từ URL: {}", imageUrl);
            return;
        }

        try {
            cloudinary.uploader().destroy(publicId, Map.of(
                    "resource_type", "image",
                    "invalidate", true
            ));
        } catch (IOException | RuntimeException ex) {
            log.warn("Không thể xóa ảnh Cloudinary có public_id={}", publicId, ex);
        }
    }

    public boolean isCloudinaryUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(imageUrl.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && "res.cloudinary.com".equalsIgnoreCase(host)
                    && uri.getPath() != null
                    && uri.getPath().contains(UPLOAD_PATH_MARKER);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String extractPublicId(String imageUrl) {
        try {
            String path = URI.create(imageUrl.trim()).getPath();
            int markerIndex = path.indexOf(UPLOAD_PATH_MARKER);
            if (markerIndex < 0) {
                return null;
            }

            String tail = path.substring(markerIndex + UPLOAD_PATH_MARKER.length());
            String[] segments = tail.split("/");
            int startIndex = 0;

            // URL secure_url chuẩn có một đoạn version dạng v1234567890 ngay sau /upload/.
            if (segments.length > 0 && segments[0].matches("v\\d+")) {
                startIndex = 1;
            }
            if (startIndex >= segments.length) {
                return null;
            }

            StringBuilder publicId = new StringBuilder();
            for (int i = startIndex; i < segments.length; i++) {
                if (segments[i].isBlank()) {
                    continue;
                }
                if (!publicId.isEmpty()) {
                    publicId.append('/');
                }
                publicId.append(segments[i]);
            }

            int lastSlash = publicId.lastIndexOf("/");
            int lastDot = publicId.lastIndexOf(".");
            if (lastDot > lastSlash) {
                publicId.delete(lastDot, publicId.length());
            }
            return publicId.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void ensureConfigured() {
        if (cloudName == null || cloudName.isBlank()
                || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cloudinary chưa được cấu hình đầy đủ trên server"
            );
        }
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "lumora";
        }
        String normalized = folder.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "lumora" : normalized;
    }

    private String normalizePublicIdPart(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replaceAll("[^a-zA-Z0-9_-]", "-");
        return normalized.isBlank() ? fallback : normalized;
    }
}
