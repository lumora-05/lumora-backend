package com.example.restaurant.service;

import com.example.restaurant.config.GoogleMapsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GoogleMapsRouteService {
    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final GoogleMapsProperties properties;
    private final RestClient restClient;

    public GoogleMapsRouteService(GoogleMapsProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getServerApiKey())
                && StringUtils.hasText(properties.getRoutesUrl())
                && StringUtils.hasText(properties.getOriginAddress());
    }

    public RouteResult computeRouteToPlace(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google Place ID không hợp lệ");
        }
        return computeRoute(Map.of("placeId", placeId.trim()));
    }

    public RouteResult computeRouteToAddress(String address) {
        if (!StringUtils.hasText(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ");
        }
        return computeRoute(Map.of("address", address.trim()));
    }

    private RouteResult computeRoute(Map<String, String> destination) {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Maps Routes API chưa được cấu hình ở backend"
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", Map.of("address", properties.getOriginAddress().trim()));
        body.put("destination", destination);
        body.put("travelMode", "DRIVE");
        body.put("routingPreference", "TRAFFIC_AWARE");
        body.put("computeAlternativeRoutes", false);
        body.put("languageCode", StringUtils.hasText(properties.getLanguageCode())
                ? properties.getLanguageCode().trim()
                : "vi");
        body.put("regionCode", StringUtils.hasText(properties.getRegionCode())
                ? properties.getRegionCode().trim()
                : "vn");
        body.put("units", "METRIC");

        try {
            JsonNode response = restClient.post()
                    .uri(properties.getRoutesUrl())
                    .header("X-Goog-Api-Key", properties.getServerApiKey().trim())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode routes = response == null ? null : response.path("routes");
            if (routes == null || !routes.isArray() || routes.size() == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Google Maps không tìm thấy tuyến đường phù hợp tới địa chỉ giao hàng"
                );
            }

            JsonNode route = routes.get(0);
            int distanceMeters = route.path("distanceMeters").asInt(0);
            long durationSeconds = parseDurationSeconds(route.path("duration").asText(""));
            String encodedPolyline = route.path("polyline").path("encodedPolyline").asText(null);
            if (distanceMeters <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Google Maps không trả về quãng đường hợp lệ"
                );
            }
            return new RouteResult(distanceMeters, durationSeconds, encodedPolyline);
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            String message = "Không thể tính tuyến đường bằng Google Maps";
            if (detail != null && !detail.isBlank()) {
                message += ". Vui lòng kiểm tra Routes API/API key";
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, ex);
        }
    }

    private long parseDurationSeconds(String value) {
        if (!StringUtils.hasText(value)) return 0L;
        String text = value.trim();
        if (text.endsWith("s")) text = text.substring(0, text.length() - 1);
        try {
            return Math.max(0L, Math.round(Double.parseDouble(text)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public record RouteResult(
            int distanceMeters,
            long durationSeconds,
            String encodedPolyline
    ) {
    }
}
