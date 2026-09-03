package com.example.restaurant.service;

import com.example.restaurant.config.PayOsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayOsGatewayService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PayOsProperties properties;
    private final ObjectMapper objectMapper;

    public PayOsGatewayService(PayOsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public int expireMinutes() {
        return Math.max(1, Math.min(properties.getExpireMinutes(), 60));
    }

    public CreatePaymentResult createPayment(long orderCode,
                                             long amount,
                                             String description,
                                             LocalDateTime expiresAt) {
        requireConfigured();
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền payOS phải lớn hơn 0");
        }

        String returnUrl = requireText(properties.getReturnUrl(), "PAYOS_RETURN_URL");
        String cancelUrl = requireText(properties.getCancelUrl(), "PAYOS_CANCEL_URL");
        long expiredAt = expiresAt.atZone(APP_ZONE).toEpochSecond();
        String signatureData = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("expiredAt", expiredAt);
        body.put("signature", hmacHex(signatureData));

        JsonNode response = post("/v2/payment-requests", body);
        ensureSuccessfulResponse(response, "Không thể tạo yêu cầu thanh toán payOS");
        JsonNode data = response.path("data");
        verifyResponseSignatureIfPresent(data, response.path("signature").asText(null));

        return new CreatePaymentResult(
                data.path("orderCode").asLong(orderCode),
                data.path("amount").asLong(amount),
                text(data, "description"),
                text(data, "bin"),
                text(data, "accountNumber"),
                text(data, "accountName"),
                text(data, "paymentLinkId"),
                text(data, "checkoutUrl"),
                text(data, "qrCode"),
                text(data, "status")
        );
    }

    public void cancelPayment(long orderCode, String reason) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancellationReason", reason == null || reason.isBlank()
                ? "Tạo yêu cầu thanh toán mới"
                : reason.trim());
        JsonNode response = post("/v2/payment-requests/" + orderCode + "/cancel", body);
        ensureSuccessfulResponse(response, "Không thể hủy yêu cầu thanh toán payOS cũ");
    }

    public JsonNode registerWebhook() {
        requireConfigured();
        String webhookUrl = requireText(properties.getWebhookUrl(), "PAYOS_WEBHOOK_URL");
        Map<String, Object> body = Map.of("webhookUrl", webhookUrl);
        JsonNode response = post("/confirm-webhook", body);
        ensureSuccessfulResponse(response, "Không thể đăng ký webhook payOS");
        return response;
    }

    public VerifiedWebhook verifyWebhook(JsonNode body) {
        requireConfigured();
        if (body == null || !body.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payOS không hợp lệ");
        }
        JsonNode data = body.get("data");
        String signature = text(body, "signature");
        if (data == null || !data.isObject() || signature == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payOS thiếu dữ liệu hoặc chữ ký");
        }

        String canonical = canonicalData((ObjectNode) data);
        String expected = hmacHex(canonical);
        if (!MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chữ ký webhook payOS không hợp lệ");
        }

        return new VerifiedWebhook(
                body.path("success").asBoolean(false),
                text(body, "code"),
                data.path("orderCode").asLong(0),
                data.path("amount").asLong(0),
                text(data, "description"),
                text(data, "reference"),
                text(data, "paymentLinkId"),
                text(data, "code"),
                text(data, "desc")
        );
    }

    private JsonNode post(String path, Object body) {
        try {
            return RestClient.builder()
                    .baseUrl(requireText(properties.getBaseUrl(), "PAYOS_BASE_URL"))
                    .defaultHeader("x-client-id", requireText(properties.getClientId(), "PAYOS_CLIENT_ID"))
                    .defaultHeader("x-api-key", requireText(properties.getApiKey(), "PAYOS_API_KEY"))
                    .build()
                    .post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            String detail = exception.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) {
                detail = exception.getStatusText();
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "payOS từ chối yêu cầu: " + detail,
                    exception
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không kết nối được payOS",
                    exception
            );
        }
    }

    private void ensureSuccessfulResponse(JsonNode response, String message) {
        if (response == null || !"00".equals(text(response, "code")) || response.get("data") == null) {
            String desc = response == null ? null : text(response, "desc");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    desc == null ? message : message + ": " + desc
            );
        }
    }

    private void verifyResponseSignatureIfPresent(JsonNode data, String signature) {
        if (data == null || !data.isObject() || signature == null || signature.isBlank()) {
            return;
        }
        String expected = hmacHex(canonicalData((ObjectNode) data));
        if (!MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Chữ ký phản hồi payOS không hợp lệ");
        }
    }

    private String canonicalData(ObjectNode objectNode) {
        List<String> keys = new ArrayList<>();
        objectNode.fieldNames().forEachRemaining(keys::add);
        keys.sort(Comparator.naturalOrder());

        List<String> parts = new ArrayList<>(keys.size());
        for (String key : keys) {
            JsonNode value = objectNode.get(key);
            parts.add(key + "=" + canonicalValue(value));
        }
        return String.join("&", parts);
    }

    private String canonicalValue(JsonNode value) {
        if (value == null || value.isNull() || "null".equalsIgnoreCase(value.asText())) {
            return "";
        }
        if (value.isArray()) {
            try {
                List<JsonNode> sortedElements = new ArrayList<>();
                for (JsonNode element : value) {
                    sortedElements.add(sortObjectKeys(element));
                }
                return objectMapper.writeValueAsString(sortedElements);
            } catch (JsonProcessingException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xác minh dữ liệu webhook payOS", exception);
            }
        }
        if (value.isObject()) {
            try {
                return objectMapper.writeValueAsString(sortObjectKeys(value));
            } catch (JsonProcessingException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xác minh dữ liệu webhook payOS", exception);
            }
        }
        return value.asText();
    }

    private JsonNode sortObjectKeys(JsonNode node) {
        if (node == null || !node.isObject()) {
            return node;
        }
        ObjectNode sorted = objectMapper.createObjectNode();
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            sorted.set(key, node.get(key));
        }
        return sorted;
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    requireText(properties.getChecksumKey(), "PAYOS_CHECKSUM_KEY").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo chữ ký payOS", exception);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình payOS. Cần PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY, PAYOS_RETURN_URL và PAYOS_CANCEL_URL"
            );
        }
    }

    private String requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Thiếu cấu hình " + environmentVariable
            );
        }
        return value.trim();
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public record CreatePaymentResult(
            long orderCode,
            long amount,
            String description,
            String bin,
            String accountNumber,
            String accountName,
            String paymentLinkId,
            String checkoutUrl,
            String qrCode,
            String status
    ) {
    }

    public record VerifiedWebhook(
            boolean success,
            String envelopeCode,
            long orderCode,
            long amount,
            String description,
            String reference,
            String paymentLinkId,
            String transactionCode,
            String transactionDescription
    ) {
        public boolean isSuccessfulPayment() {
            return success && "00".equals(envelopeCode) && "00".equals(transactionCode);
        }
    }
}
