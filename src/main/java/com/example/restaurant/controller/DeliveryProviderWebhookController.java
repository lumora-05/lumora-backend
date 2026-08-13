package com.example.restaurant.controller;

import com.example.restaurant.config.DeliveryProperties;
import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DeliveryProviderStatusRequest;
import com.example.restaurant.entity.Order;
import com.example.restaurant.service.DeliveryOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/delivery-provider")
public class DeliveryProviderWebhookController {
    private final DeliveryOrderService deliveryOrderService;
    private final DeliveryProperties deliveryProperties;

    public DeliveryProviderWebhookController(DeliveryOrderService deliveryOrderService,
                                             DeliveryProperties deliveryProperties) {
        this.deliveryOrderService = deliveryOrderService;
        this.deliveryProperties = deliveryProperties;
    }

    /** Webhook mô phỏng đối tác giao hàng; token tách biệt với JWT nhân viên. */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Order>> webhook(
            @RequestHeader(value = "X-Delivery-Webhook-Token", required = false) String token,
            @Valid @RequestBody DeliveryProviderStatusRequest request) {
        verifyToken(token);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đồng bộ trạng thái từ đơn vị vận chuyển",
                deliveryOrderService.applyProviderWebhook(request)
        ));
    }

    private void verifyToken(String provided) {
        String expected = deliveryProperties.getProviderWebhookToken();
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook token không hợp lệ");
        }
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook token không hợp lệ");
        }
    }
}
