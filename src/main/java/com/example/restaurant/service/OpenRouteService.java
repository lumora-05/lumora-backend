package com.example.restaurant.service;

import com.example.restaurant.config.OpenRouteServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class OpenRouteService {
    private final OpenRouteServiceProperties properties;
    private final RestClient restClient;
    private volatile Coordinate originCache;

    public OpenRouteService(OpenRouteServiceProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getDirectionsUrl())
                && StringUtils.hasText(properties.getGeocodeUrl())
                && StringUtils.hasText(properties.getOriginAddress());
    }

    public RouteResult computeRouteToAddress(String address) {
        if (!StringUtils.hasText(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ");
        }
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "openrouteservice chưa được cấu hình ở backend"
            );
        }

        Coordinate origin = originCoordinate();
        GeocodeResult destination = geocode(address.trim());
        Map<String, Object> body = Map.of(
                "coordinates", List.of(
                        List.of(origin.longitude(), origin.latitude()),
                        List.of(destination.coordinate().longitude(), destination.coordinate().latitude())
                ),
                "instructions", false
        );

        try {
            JsonNode response = restClient.post()
                    .uri(properties.getDirectionsUrl().trim())
                    .header("Authorization", properties.getApiKey().trim())
                    .header("Accept", "application/geo+json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode features = response == null ? null : response.path("features");
            if (features == null || !features.isArray() || features.size() == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không tìm thấy tuyến đường phù hợp tới địa chỉ giao hàng"
                );
            }

            JsonNode feature = features.get(0);
            JsonNode summary = feature.path("properties").path("summary");
            int distanceMeters = (int) Math.round(summary.path("distance").asDouble(0d));
            long durationSeconds = Math.round(summary.path("duration").asDouble(0d));
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            if (distanceMeters <= 0 || coordinates == null || !coordinates.isArray() || coordinates.size() == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dịch vụ bản đồ không trả về tuyến đường hợp lệ"
                );
            }

            return new RouteResult(
                    distanceMeters,
                    Math.max(0L, durationSeconds),
                    coordinates.toString(),
                    destination.formattedAddress()
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể tính tuyến đường bằng openrouteservice. Vui lòng kiểm tra OPENROUTESERVICE_API_KEY",
                    ex
            );
        }
    }

    private Coordinate originCoordinate() {
        Coordinate cached = originCache;
        if (cached != null) return cached;
        synchronized (this) {
            if (originCache == null) {
                originCache = geocode(properties.getOriginAddress()).coordinate();
            }
            return originCache;
        }
    }

    private GeocodeResult geocode(String address) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getGeocodeUrl().trim())
                .queryParam("text", address)
                .queryParam("size", 1)
                .queryParam("boundary.country", StringUtils.hasText(properties.getCountryCode())
                        ? properties.getCountryCode().trim()
                        : "VN")
                .queryParam("lang", StringUtils.hasText(properties.getLanguageCode())
                        ? properties.getLanguageCode().trim()
                        : "vi")
                .build()
                .encode()
                .toUri();

        try {
            JsonNode response = restClient.get()
                    .uri(uri)
                    .header("Authorization", properties.getApiKey().trim())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode features = response == null ? null : response.path("features");
            if (features == null || !features.isArray() || features.size() == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không tìm thấy địa chỉ này trên bản đồ. Vui lòng nhập cụ thể hơn"
                );
            }

            JsonNode feature = features.get(0);
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            if (!coordinates.isArray() || coordinates.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tọa độ địa chỉ không hợp lệ");
            }
            double longitude = coordinates.get(0).asDouble(Double.NaN);
            double latitude = coordinates.get(1).asDouble(Double.NaN);
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tọa độ địa chỉ không hợp lệ");
            }

            JsonNode propertiesNode = feature.path("properties");
            String label = propertiesNode.path("label").asText("");
            if (!StringUtils.hasText(label)) label = propertiesNode.path("name").asText(address);
            return new GeocodeResult(new Coordinate(longitude, latitude), label.trim());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể xác định địa chỉ bằng openrouteservice. Vui lòng kiểm tra OPENROUTESERVICE_API_KEY",
                    ex
            );
        }
    }

    private record Coordinate(double longitude, double latitude) {
    }

    private record GeocodeResult(Coordinate coordinate, String formattedAddress) {
    }

    public record RouteResult(
            int distanceMeters,
            long durationSeconds,
            String routeGeometry,
            String resolvedAddress
    ) {
    }
}
