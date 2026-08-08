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
import java.util.Set;

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

    private static final String FOOD_CLOUDINARY_FOLDER = "lumora/foods";
    private static final String AVATAR_CLOUDINARY_FOLDER = "lumora/avatars";
    private static final String BRANDING_CLOUDINARY_FOLDER = "lumora/branding";
    private static final String LEGACY_FOOD_PREFIX = "/uploads/foods/";
    private static final String LEGACY_AVATAR_PREFIX = "/uploads/avatars/";

    private final CloudinaryImageService cloudinaryImageService;
    private final Path foodUploadDir;
    private final Path avatarUploadDir;

    public FileStorageService(
            CloudinaryImageService cloudinaryImageService,
            @Value("${app.upload.food-dir:uploads/foods}") String foodUploadDir,
            @Value("${app.upload.avatar-dir:uploads/avatars}") String avatarUploadDir) {
        this.cloudinaryImageService = cloudinaryImageService;
        this.foodUploadDir = Paths.get(foodUploadDir).toAbsolutePath().normalize();
        this.avatarUploadDir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
    }

    public String saveFoodImage(MultipartFile file) {
        return saveCloudinaryImage(file, FOOD_CLOUDINARY_FOLDER, "food", "món ăn");
    }

    public String saveAvatarImage(MultipartFile file) {
        return saveCloudinaryImage(file, AVATAR_CLOUDINARY_FOLDER, "avatar", "đại diện");
    }

    public String saveBrandLogoImage(MultipartFile file) {
        return saveCloudinaryImage(file, BRANDING_CLOUDINARY_FOLDER, "logo", "logo nhà hàng");
    }

    public String saveHomeBannerImage(MultipartFile file) {
        return saveCloudinaryImage(file, BRANDING_CLOUDINARY_FOLDER, "banner", "banner trang chủ");
    }

    public void deleteFoodImage(String imageUrl) {
        deleteImage(imageUrl, foodUploadDir, LEGACY_FOOD_PREFIX);
    }

    public void deleteAvatarImage(String imageUrl) {
        deleteImage(imageUrl, avatarUploadDir, LEGACY_AVATAR_PREFIX);
    }

    public void deleteBrandImage(String imageUrl) {
        if (cloudinaryImageService.isCloudinaryUrl(imageUrl)) {
            cloudinaryImageService.deleteByUrl(imageUrl);
        }
    }

    public void deleteBrandImageIfDifferent(String oldImageUrl, String newImageUrl) {
        if (oldImageUrl != null && !oldImageUrl.equals(newImageUrl)) {
            deleteBrandImage(oldImageUrl);
        }
    }

    private String saveCloudinaryImage(MultipartFile file,
                                       String cloudinaryFolder,
                                       String publicIdPrefix,
                                       String imageLabel) {
        validateImage(file, imageLabel);

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()
        );
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ cho phép ảnh jpg, jpeg, png, webp hoặc gif"
            );
        }

        try {
            return cloudinaryImageService.upload(file.getBytes(), cloudinaryFolder, publicIdPrefix);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể đọc ảnh " + imageLabel,
                    ex
            );
        }
    }

    /**
     * Xóa ảnh mới trên Cloudinary; vẫn hỗ trợ dọn các đường dẫn local cũ trong
     * thời gian chuyển dữ liệu để không làm ảnh hưởng dữ liệu đã tồn tại.
     */
    private void deleteImage(String imageUrl, Path legacyUploadDir, String legacyPublicPrefix) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        if (cloudinaryImageService.isCloudinaryUrl(imageUrl)) {
            cloudinaryImageService.deleteByUrl(imageUrl);
            return;
        }

        if (!imageUrl.startsWith(legacyPublicPrefix)) {
            return;
        }

        String fileName = imageUrl.substring(legacyPublicPrefix.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }

        Path target = legacyUploadDir.resolve(fileName).normalize();
        if (!target.startsWith(legacyUploadDir)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Không làm hỏng nghiệp vụ nếu việc dọn ảnh cũ thất bại.
        }
    }

    private void validateImage(MultipartFile file, String imageLabel) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File ảnh " + imageLabel + " không được bỏ trống"
            );
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh không được vượt quá 5 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File tải lên không phải định dạng ảnh được hỗ trợ"
            );
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
