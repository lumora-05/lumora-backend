package com.example.restaurant.service;

import com.example.restaurant.entity.DiningTable;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class QrCodeService {
    private static final String QR_CLOUDINARY_FOLDER = "lumora/qrcodes";
    private static final String LEGACY_QR_PREFIX = "/uploads/qrcodes/";

    private final String frontendBaseUrl;
    private final Path qrUploadDir;
    private final int qrSize;
    private final CloudinaryImageService cloudinaryImageService;

    public QrCodeService(
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.upload.qr-dir:uploads/qrcodes}") String qrUploadDir,
            @Value("${app.qr.size:300}") int qrSize,
            CloudinaryImageService cloudinaryImageService) {
        this.frontendBaseUrl = frontendBaseUrl;
        this.qrUploadDir = Paths.get(qrUploadDir).toAbsolutePath().normalize();
        this.qrSize = qrSize;
        this.cloudinaryImageService = cloudinaryImageService;
    }

    /**
     * Nội dung QR dùng token ngẫu nhiên riêng của bàn, không chứa mã bàn có thể đoán.
     * Route frontend giữ dạng /table/{value} để chỉ cần đổi giá trị từ maBan sang token.
     */
    public String buildQrContent(DiningTable table) {
        if (table == null || table.getMaBan() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn ăn chưa có mã bàn để tạo QR");
        }
        if (table.getQrToken() == null || table.getQrToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bàn ăn chưa có QR token");
        }
        return removeTrailingSlash(frontendBaseUrl) + "/table/" + table.getQrToken();
    }

    public String generateTableQr(DiningTable table) {
        String content = buildQrContent(table);
        String oldImageUrl = table.getAnhQr();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize);
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            String newImageUrl = cloudinaryImageService.upload(
                    outputStream.toByteArray(),
                    QR_CLOUDINARY_FOLDER,
                    "table-" + table.getMaBan(),
                    "png"
            );

            deleteOldImage(oldImageUrl, newImageUrl);
            return newImageUrl;
        } catch (WriterException | IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể tạo ảnh QR cho bàn ăn",
                    ex
            );
        }
    }

    public void deleteTableQrImage(DiningTable table) {
        if (table != null) {
            deleteOldImage(table.getAnhQr(), null);
        }
    }

    public boolean isCloudinaryImage(String imageUrl) {
        return cloudinaryImageService.isCloudinaryUrl(imageUrl);
    }

    private void deleteOldImage(String imageUrl, String keepImageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.equals(keepImageUrl)) {
            return;
        }

        if (cloudinaryImageService.isCloudinaryUrl(imageUrl)) {
            cloudinaryImageService.deleteByUrl(imageUrl);
            return;
        }

        // Hỗ trợ dọn ảnh QR local cũ trong thời gian chuyển sang Cloudinary.
        if (!imageUrl.startsWith(LEGACY_QR_PREFIX)) {
            return;
        }

        String fileName = imageUrl.substring(LEGACY_QR_PREFIX.length()).split("\\?", 2)[0];
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }

        Path oldFile = qrUploadDir.resolve(fileName).normalize();
        if (!oldFile.startsWith(qrUploadDir)) {
            return;
        }

        try {
            Files.deleteIfExists(oldFile);
        } catch (IOException ignored) {
            // Không làm hỏng nghiệp vụ nếu file cũ không xóa được.
        }
    }

    private String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:5173";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
