package com.example.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private final Path foodUploadDir;
    private final Path avatarUploadDir;

    public FileStorageService(
            @Value("${app.upload.food-dir:uploads/foods}") String foodUploadDir,
            @Value("${app.upload.avatar-dir:uploads/avatars}") String avatarUploadDir) {
        this.foodUploadDir = Paths.get(foodUploadDir).toAbsolutePath().normalize();
        this.avatarUploadDir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
    }

    public String saveFoodImage(MultipartFile file) {
        return saveImage(file, foodUploadDir, "/uploads/foods/", "món ăn");
    }

    public String saveAvatarImage(MultipartFile file) {
        return saveImage(file, avatarUploadDir, "/uploads/avatars/", "đại diện");
    }

    public void deleteAvatarImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("/uploads/avatars/")) {
            return;
        }

        String fileName = imageUrl.substring("/uploads/avatars/".length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }

        Path target = avatarUploadDir.resolve(fileName).normalize();
        if (!target.startsWith(avatarUploadDir)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Không làm hỏng thao tác cập nhật hồ sơ nếu việc dọn ảnh cũ thất bại.
        }
    }

    private String saveImage(MultipartFile file,
                             Path uploadDir,
                             String publicPrefix,
                             String imageLabel) {
        validateImage(file, imageLabel);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ cho phép ảnh jpg, jpeg, png, webp hoặc gif");
        }

        try {
            Files.createDirectories(uploadDir);
            String fileName = UUID.randomUUID() + "." + extension;
            Path target = uploadDir.resolve(fileName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file ảnh không hợp lệ");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return publicPrefix + fileName;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh " + imageLabel);
        }
    }

    private void validateImage(MultipartFile file, String imageLabel) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File ảnh " + imageLabel + " không được bỏ trống");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh không được vượt quá 5 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File tải lên không phải định dạng ảnh được hỗ trợ");
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File ảnh không có định dạng hợp lệ");
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
}
