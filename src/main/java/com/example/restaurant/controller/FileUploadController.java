package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class FileUploadController {
    private final FileStorageService fileStorageService;

    @PostMapping("/foods")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFoodImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileStorageService.saveFoodImage(file);
        return ResponseEntity.ok(ApiResponse.success(
                "Upload ảnh món ăn thành công",
                Map.of("url", imageUrl)
        ));
    }
}
