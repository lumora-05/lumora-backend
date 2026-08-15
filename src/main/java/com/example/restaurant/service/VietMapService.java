package com.example.restaurant.service;

import com.example.restaurant.config.RestaurantInfoProperties;
import com.example.restaurant.config.VietMapProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class VietMapService {
    private static final long ADDRESS_SELECTION_TTL_MILLIS = 20L * 60L * 1000L;

    private final VietMapProperties properties;
    private final RestaurantInfoProperties restaurantInfoProperties;
    private final RestClient restClient;
    private final String selectionSigningSecret;

    public VietMapService(
            VietMapProperties properties,
            RestaurantInfoProperties restaurantInfoProperties,
            @Value("${app.jwt.secret}") String selectionSigningSecret) {
        this.properties = properties;
        this.restaurantInfoProperties = restaurantInfoProperties;
        this.selectionSigningSecret = selectionSigningSecret;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getPlaceUrl())
                && StringUtils.hasText(properties.getRouteUrl())
                && validOrigin();
    }

    public boolean isAutocompleteConfigured() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getAutocompleteUrl());
    }

    public List<AddressSuggestion> suggestAddresses(String query, String requiredWard, String requiredCity) {
        if (!StringUtils.hasText(query) || cleanupSpaces(query).length() < 3) {
            return List.of();
        }
        if (!isAutocompleteConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VietMap chưa được cấu hình ở backend");
        }

        String city = cleanupSpaces(requiredCity);
        String ward = cleanupSpaces(requiredWard);
        // Không khóa cứng phường/xã vào chuỗi tìm kiếm: địa giới mới/cũ có thể khác tên.
        // Chỉ thêm tỉnh/thành phố, sau đó ưu tiên kết quả khớp phường/xã ở phía backend.
        StringBuilder text = new StringBuilder(cleanupSpaces(query));
        if (StringUtils.hasText(city)) {
            text.append(", ").append(city);
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getAutocompleteUrl().trim())
                .queryParam("apikey", properties.getApiKey().trim())
                .queryParam("text", text.toString())
                .queryParam("display_type", displayType());
        if (validOrigin()) {
            builder.queryParam("focus", properties.getOriginLatitude() + "," + properties.getOriginLongitude());
        }

        try {
            JsonNode response = restClient.get()
                    .uri(builder.build().encode().toUri())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.isArray() || response.isEmpty()) {
                return List.of();
            }

            List<AddressSuggestion> wardMatches = new ArrayList<>();
            List<AddressSuggestion> cityMatches = new ArrayList<>();
            Set<String> dedupe = new LinkedHashSet<>();

            for (JsonNode item : response) {
                String refId = cleanupSpaces(item.path("ref_id").asText(""));
                String display = cleanupSpaces(item.path("display").asText(""));
                String name = cleanupSpaces(item.path("name").asText(""));
                if (!StringUtils.hasText(refId) || !StringUtils.hasText(display)) {
                    continue;
                }

                String itemCity = boundaryName(item, 0);
                String itemWard = boundaryName(item, 2);
                if (!matchesLocation(itemCity, display, city)) {
                    continue;
                }

                String oldDisplay = cleanupSpaces(item.path("data_old").path("display").asText(""));
                boolean wardMatched = !StringUtils.hasText(ward)
                        || matchesLocation(itemWard, display, ward)
                        || matchesLocation("", oldDisplay, ward);

                String houseNumber = extractLeadingHouseNumber(name);
                String street = extractStreetName(name);
                String label = display;
                String token = createSelectionToken(refId, display, itemWard, itemCity);

                String dedupeKey = normalizeText(display);
                if (!dedupe.add(dedupeKey)) {
                    continue;
                }

                AddressSuggestion suggestion = new AddressSuggestion(
                        label,
                        name,
                        houseNumber,
                        street,
                        itemWard,
                        itemCity,
                        null,
                        null,
                        token);

                if (wardMatched) {
                    wardMatches.add(suggestion);
                } else {
                    cityMatches.add(suggestion);
                }
            }

            List<AddressSuggestion> selected = !wardMatches.isEmpty() ? wardMatches : cityMatches;
            return selected.size() <= 6
                    ? List.copyOf(selected)
                    : List.copyOf(selected.subList(0, 6));
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể tải gợi ý địa chỉ từ VietMap",
                    ex);
        }
    }

    public RouteResult computeRouteToSelection(String selectionToken, String requiredWard, String requiredCity) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VietMap chưa được cấu hình ở backend");
        }

        AddressSelection selection = verifySelectionToken(selectionToken);
        PlaceResult destination = fetchPlace(selection.refId());
        verifyDestinationCity(destination, requiredCity);
        return computeRoute(destination);
    }

    /** Tương thích frontend cũ chưa có selectionToken. */
    public RouteResult computeRouteToAddress(String address) {
        return computeRouteToAddress(address, null, null);
    }

    /** Tương thích frontend cũ: tìm địa chỉ bằng Search v4 rồi lấy chi tiết bằng Place v4. */
    public RouteResult computeRouteToAddress(String address, String requiredWard, String requiredCity) {
        if (!StringUtils.hasText(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ");
        }
        if (!isConfigured() || !StringUtils.hasText(properties.getSearchUrl())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VietMap chưa được cấu hình ở backend");
        }

        StringBuilder text = new StringBuilder(cleanupSpaces(address));
        if (StringUtils.hasText(requiredWard) && !normalizeText(address).contains(normalizeText(requiredWard))) {
            text.append(", ").append(cleanupSpaces(requiredWard));
        }
        if (StringUtils.hasText(requiredCity) && !normalizeText(address).contains(normalizeText(requiredCity))) {
            text.append(", ").append(cleanupSpaces(requiredCity));
        }

        URI uri = UriComponentsBuilder.fromUriString(properties.getSearchUrl().trim())
                .queryParam("apikey", properties.getApiKey().trim())
                .queryParam("text", text.toString())
                .queryParam("display_type", displayType())
                .queryParam("focus", properties.getOriginLatitude() + "," + properties.getOriginLongitude())
                .build().encode().toUri();

        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null || !response.isArray() || response.isEmpty()) {
                throw addressNotFound();
            }

            for (JsonNode item : response) {
                String refId = cleanupSpaces(item.path("ref_id").asText(""));
                String display = cleanupSpaces(item.path("display").asText(""));
                String itemCity = boundaryName(item, 0);
                if (!StringUtils.hasText(refId) || !matchesLocation(itemCity, display, requiredCity)) {
                    continue;
                }
                PlaceResult destination = fetchPlace(refId);
                verifyDestinationCity(destination, requiredCity);
                return computeRoute(destination);
            }
            throw addressNotFound();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể tìm địa chỉ trên VietMap", ex);
        }
    }

    private PlaceResult fetchPlace(String refId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getPlaceUrl().trim())
                .queryParam("apikey", properties.getApiKey().trim())
                .queryParam("refid", refId)
                .build().encode().toUri();
        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null) {
                throw addressNotFound();
            }
            double lat = response.path("lat").asDouble(Double.NaN);
            double lng = response.path("lng").asDouble(Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                throw addressNotFound();
            }
            return new PlaceResult(
                    new Coordinate(lng, lat),
                    cleanupSpaces(response.path("display").asText("")),
                    cleanupSpaces(response.path("ward").asText("")),
                    cleanupSpaces(response.path("city").asText("")));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể lấy tọa độ địa chỉ từ VietMap", ex);
        }
    }

    private RouteResult computeRoute(PlaceResult destination) {
        Coordinate origin = new Coordinate(properties.getOriginLongitude(), properties.getOriginLatitude());
        int directDistanceMeters = (int) Math.round(haversineDistanceMeters(origin, destination.coordinate()));

        URI uri = UriComponentsBuilder.fromUriString(properties.getRouteUrl().trim())
                .queryParam("apikey", properties.getApiKey().trim())
                .queryParam("point", origin.latitude() + "," + origin.longitude())
                .queryParam("point", destination.coordinate().latitude() + "," + destination.coordinate().longitude())
                .queryParam("points_encoded", false)
                .queryParam("vehicle", "car")
                .build().encode().toUri();

        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null || !"OK".equalsIgnoreCase(response.path("code").asText(""))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy tuyến đường phù hợp tới địa chỉ giao hàng");
            }
            JsonNode paths = response.path("paths");
            if (!paths.isArray() || paths.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy tuyến đường phù hợp tới địa chỉ giao hàng");
            }

            JsonNode path = paths.get(0);
            double rawDistance = path.path("distance").asDouble(Double.NaN);
            if (!Double.isFinite(rawDistance) || rawDistance < 0d) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VietMap không trả về khoảng cách hợp lệ");
            }
            int distanceMeters = Math.max((int) Math.round(rawDistance), directDistanceMeters);
            long durationSeconds = path.path("time").isNumber()
                    ? Math.max(0L, Math.round(path.path("time").asDouble() / 1000d))
                    : 0L;

            String routeGeometry = toLongitudeLatitudeGeometry(path.path("points"));
            if (!StringUtils.hasText(routeGeometry)) {
                routeGeometry = buildFallbackGeometry(origin, destination.coordinate());
            }

            String context = String.join(", ", nonBlank(destination.ward(), destination.city()));
            return new RouteResult(
                    Math.max(1, distanceMeters),
                    durationSeconds,
                    routeGeometry,
                    destination.display(),
                    context);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể kết nối VietMap để tính tuyến giao hàng", ex);
        }
    }

    /** VietMap points_encoded=false trả [lat,lng]; frontend cũ của dự án đang nhận [lng,lat]. */
    private String toLongitudeLatitudeGeometry(JsonNode points) {
        if (points == null || !points.isArray() || points.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (JsonNode point : points) {
            if (!point.isArray() || point.size() < 2) {
                continue;
            }
            double lat = point.get(0).asDouble(Double.NaN);
            double lng = point.get(1).asDouble(Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            out.append('[').append(lng).append(',').append(lat).append(']');
            first = false;
        }
        out.append(']');
        return first ? null : out.toString();
    }

    private void verifyDestinationCity(PlaceResult destination, String requiredCity) {
        if (StringUtils.hasText(requiredCity)
                && !matchesLocation(destination.city(), destination.display(), requiredCity)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Địa chỉ đã chọn không thuộc tỉnh/thành phố giao hàng");
        }
    }

    private String createSelectionToken(String refId, String label, String ward, String city) {
        long expiresAt = System.currentTimeMillis() + ADDRESS_SELECTION_TTL_MILLIS;
        String payload = refId + "\n" + cleanupSpaces(label) + "\n" + cleanupSpaces(ward) + "\n"
                + cleanupSpaces(city) + "\n" + expiresAt;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    private AddressSelection verifySelectionToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw invalidSelectionToken();
        }
        String[] parts = token.trim().split("\\.", 2);
        if (parts.length != 2) {
            throw invalidSelectionToken();
        }
        String expected = sign(parts[0]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw invalidSelectionToken();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\n", -1);
            if (fields.length != 5 || !StringUtils.hasText(fields[0])) {
                throw invalidSelectionToken();
            }
            long expiresAt = Long.parseLong(fields[4]);
            if (System.currentTimeMillis() > expiresAt) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ đã chọn đã hết hạn. Vui lòng chọn lại địa chỉ");
            }
            return new AddressSelection(fields[0], fields[1], fields[2], fields[3]);
        } catch (IllegalArgumentException ex) {
            throw invalidSelectionToken();
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(selectionSigningSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể ký lựa chọn địa chỉ", ex);
        }
    }

    private String boundaryName(JsonNode item, int type) {
        JsonNode boundaries = item.path("boundaries");
        if (!boundaries.isArray()) {
            return "";
        }
        for (JsonNode boundary : boundaries) {
            if (boundary.path("type").asInt(Integer.MIN_VALUE) == type) {
                String fullName = cleanupSpaces(boundary.path("full_name").asText(""));
                return StringUtils.hasText(fullName) ? fullName : cleanupSpaces(boundary.path("name").asText(""));
            }
        }
        return "";
    }

    private boolean matchesLocation(String structuredValue, String display, String required) {
        if (!StringUtils.hasText(required)) {
            return true;
        }
        String needle = normalizeText(required);
        return normalizeText(structuredValue).contains(needle) || normalizeText(display).contains(needle);
    }

    private String extractLeadingHouseNumber(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String first = cleanupSpaces(text).split("\\s+", 2)[0];
        return first.chars().anyMatch(Character::isDigit) ? first : "";
    }

    private String extractStreetName(String text) {
        String cleaned = cleanupSpaces(text);
        String house = extractLeadingHouseNumber(cleaned);
        if (StringUtils.hasText(house) && cleaned.length() > house.length()) {
            cleaned = cleanupSpaces(cleaned.substring(house.length()));
        }
        return cleaned.replaceFirst("(?iu)^đường\\s+", "");
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();
        return normalized.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String cleanupSpaces(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ") : "";
    }

    private boolean validOrigin() {
        Double lat = properties.getOriginLatitude();
        Double lng = properties.getOriginLongitude();
        return lat != null && lng != null && Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90d && lat <= 90d && lng >= -180d && lng <= 180d;
    }

    private int displayType() {
        return properties.getDisplayType() == null ? 5 : properties.getDisplayType();
    }

    private List<String> nonBlank(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(cleanupSpaces(value));
            }
        }
        return result;
    }

    private double haversineDistanceMeters(Coordinate from, Coordinate to) {
        final double earthRadiusMeters = 6_371_000d;
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2d) * Math.sin(deltaLon / 2d);
        return earthRadiusMeters * (2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a)));
    }

    private String buildFallbackGeometry(Coordinate from, Coordinate to) {
        return "[[" + from.longitude() + "," + from.latitude() + "],["
                + to.longitude() + "," + to.latitude() + "]]";
    }

    private ResponseStatusException invalidSelectionToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ đã chọn không hợp lệ. Vui lòng chọn lại từ danh sách gợi ý");
    }

    private ResponseStatusException addressNotFound() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không xác định được chính xác địa chỉ giao hàng trên VietMap. Vui lòng chọn một địa chỉ trong danh sách gợi ý");
    }

    private record AddressSelection(String refId, String label, String ward, String city) {}
    private record Coordinate(double longitude, double latitude) {}
    private record PlaceResult(Coordinate coordinate, String display, String ward, String city) {}

    public record AddressSuggestion(
            String label,
            String name,
            String houseNumber,
            String street,
            String ward,
            String city,
            Double latitude,
            Double longitude,
            String selectionToken) {}

    public record RouteResult(
            int distanceMeters,
            long durationSeconds,
            String routeGeometry,
            String resolvedAddress,
            String resolvedLocationContext) {}
}
