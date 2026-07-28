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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class QrCodeService {
    private final String frontendBaseUrl;
    private final Path qrUploadDir;
    private final int qrSize;

    public QrCodeService(@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
                         @Value("${app.upload.qr-dir:uploads/qrcodes}") String qrUploadDir,
                         @Value("${app.qr.size:300}") int qrSize) {
        this.frontendBaseUrl = frontendBaseUrl;
        this.qrUploadDir = Paths.get(qrUploadDir).toAbsolutePath().normalize();
        this.qrSize = qrSize;
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
        String fileName = "table-" + table.getMaBan() + "-" + System.currentTimeMillis() + ".png";
        Path target = qrUploadDir.resolve(fileName).normalize();

        if (!target.startsWith(qrUploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn tạo QR không hợp lệ");
        }

        try {
            Files.createDirectories(qrUploadDir);
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize);
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", target);
            deleteOldImage(table.getAnhQr(), target);
            return "/uploads/qrcodes/" + fileName;
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo ảnh QR cho bàn ăn");
        }
    }

    public void deleteTableQrImage(DiningTable table) {
        if (table != null) {
            deleteOldImage(table.getAnhQr(), null);
        }
    }

    private void deleteOldImage(String publicPath, Path keepPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return;
        }

        String fileName = publicPath.substring(publicPath.lastIndexOf('/') + 1).split("\\?", 2)[0];
        if (fileName.isBlank()) {
            return;
        }

        Path oldFile = qrUploadDir.resolve(fileName).normalize();
        if (!oldFile.startsWith(qrUploadDir) || (keepPath != null && oldFile.equals(keepPath))) {
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
