package com.example.restaurant.service;

import com.example.restaurant.config.OpenRouteServiceProperties;
import com.example.restaurant.config.RestaurantInfoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenRouteService {

    private final OpenRouteServiceProperties properties;
    private final RestaurantInfoProperties restaurantInfoProperties;
    private final RestClient restClient;

    /**
     * Cache tọa độ nhà hàng theo chính địa chỉ đang có trong Cài đặt hệ thống.
     * Khi admin đổi địa chỉ, key cache thay đổi và vị trí được geocode lại tự động.
     */
    private volatile Coordinate originCache;
    private volatile String originCacheAddress;

    public OpenRouteService(
            OpenRouteServiceProperties properties,
            RestaurantInfoProperties restaurantInfoProperties) {
        this.properties = properties;
        this.restaurantInfoProperties = restaurantInfoProperties;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getDirectionsUrl())
                && StringUtils.hasText(properties.getGeocodeUrl());
    }

    public RouteResult computeRouteToAddress(String address) {
        if (!StringUtils.hasText(address)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Địa chỉ giao hàng không hợp lệ");
        }

        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "openrouteservice chưa được cấu hình ở backend");
        }

        // Luôn lấy điểm xuất phát từ địa chỉ nhà hàng hiện tại trong Cài đặt hệ thống.
        Coordinate origin = originCoordinate();

        // Ưu tiên kết quả địa chỉ khách gần nhà hàng để tránh nhầm địa danh trùng tên.
        GeocodeResult destination = geocodeDeliveryAddress(address.trim(), origin);

        Map<String, Object> body = Map.of(
                "coordinates", List.of(
                        List.of(origin.longitude(), origin.latitude()),
                        List.of(destination.coordinate().longitude(), destination.coordinate().latitude())),
                "instructions", false);

        try {
            JsonNode response = restClient.post()
                    .uri(properties.getDirectionsUrl().trim())
                    .header("Authorization", properties.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/geo+json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode features = response == null ? null : response.path("features");
            if (features == null || !features.isArray() || features.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không tìm thấy tuyến đường phù hợp tới địa chỉ giao hàng");
            }

            JsonNode feature = features.get(0);
            JsonNode summary = feature.path("properties").path("summary");

            int distanceMeters = (int) Math.round(summary.path("distance").asDouble(0d));
            long durationSeconds = Math.round(summary.path("duration").asDouble(0d));

            JsonNode coordinates = feature.path("geometry").path("coordinates");
            if (distanceMeters <= 0
                    || coordinates == null
                    || !coordinates.isArray()
                    || coordinates.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dịch vụ bản đồ không trả về tuyến đường hợp lệ");
            }

            return new RouteResult(
                    distanceMeters,
                    Math.max(0L, durationSeconds),
                    coordinates.toString(),
                    destination.formattedAddress());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể tính tuyến đường bằng openrouteservice. "
                            + "Vui lòng kiểm tra OPENROUTESERVICE_API_KEY",
                    ex);
        }
    }

    /**
     * Lấy địa chỉ nhà hàng từ RestaurantInfoProperties.
     * SystemSettingService đồng bộ trường này từ bảng cai_dat_he_thong khi khởi động
     * và ngay sau khi admin lưu Cài đặt hệ thống.
     */
    private Coordinate originCoordinate() {
        String configuredAddress = currentRestaurantAddress();

        Coordinate cached = originCache;
        String cachedAddress = originCacheAddress;
        if (cached != null && configuredAddress.equals(cachedAddress)) {
            return cached;
        }

        synchronized (this) {
            // Đọc lại trong synchronized để nếu admin vừa cập nhật địa chỉ thì dùng bản mới nhất.
            configuredAddress = currentRestaurantAddress();
            if (originCache == null || !configuredAddress.equals(originCacheAddress)) {
                GeocodeResult origin = geocodeWithFallback(
                        configuredAddress,
                        null,
                        GeocodeTarget.RESTAURANT);
                originCache = origin.coordinate();
                originCacheAddress = configuredAddress;
            }
            return originCache;
        }
    }

    private String currentRestaurantAddress() {
        String address = cleanupSpaces(restaurantInfoProperties.getAddress());
        if (!StringUtils.hasText(address)
                || "chưa cập nhật".equalsIgnoreCase(address)
                || "chua cap nhat".equalsIgnoreCase(removeVietnameseDiacritics(address))) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Địa chỉ nhà hàng chưa được cấu hình trong Cài đặt hệ thống");
        }
        return address;
    }

    /**
     * Geocode địa chỉ khách và bias kết quả về gần vị trí nhà hàng.
     */
    private GeocodeResult geocodeDeliveryAddress(String address, Coordinate origin) {
        return geocodeWithFallback(address, origin, GeocodeTarget.DELIVERY);
    }

    /**
     * Thử nhiều cách viết của cùng một địa chỉ.
     *
     * Với địa chỉ đầy đủ nhưng Pelias chỉ trả kết quả quá chung như "Đà Nẵng, Việt Nam",
     * kết quả đó bị bỏ qua để thử candidate tiếp theo, ví dụ:
     * 137 Đường Nguyễn Thị Thập, Thanh Khê, Đà Nẵng
     * -> 137 Đường Nguyễn Thị Thập, Đà Nẵng
     * -> 137 Đường Nguyễn Thị Thập
     */
    private GeocodeResult geocodeWithFallback(
            String address,
            Coordinate focusPoint,
            GeocodeTarget target) {
        if (!StringUtils.hasText(address)) {
            throw geocodeNotFound(target);
        }

        Set<String> candidates = buildAddressCandidates(address);

        for (String candidate : candidates) {
            GeocodeResult result = tryGeocode(candidate, focusPoint);
            if (result != null) {
                return result;
            }
        }

        throw geocodeNotFound(target);
    }

    /**
     * Sinh các biến thể tìm kiếm theo thứ tự ưu tiên, vẫn giữ số nhà.
     */
    private Set<String> buildAddressCandidates(String rawAddress) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        String original = cleanupSpaces(rawAddress);
        String withoutRoadWord = cleanupSpaces(
                original.replaceAll("(?iu)\\bđường\\s+", ""));

        String cityFallback = keepStreetAndCity(original);
        String cityFallbackWithoutRoadWord = keepStreetAndCity(withoutRoadWord);

        // Quan trọng cho trường hợp người dùng nhập đầy đủ nhưng dữ liệu địa giới
        // của OSM/Pelias chưa đồng nhất: thử lại đúng số nhà + tên đường.
        String streetOnly = keepStreetOnly(original);
        String streetOnlyWithoutRoadWord = keepStreetOnly(withoutRoadWord);

        addCandidate(candidates, original);
        addCandidate(candidates, withoutRoadWord);
        addCandidate(candidates, cityFallback);
        addCandidate(candidates, cityFallbackWithoutRoadWord);
        addCandidate(candidates, streetOnly);
        addCandidate(candidates, streetOnlyWithoutRoadWord);

        addCandidate(candidates, appendVietnam(original));
        addCandidate(candidates, appendVietnam(withoutRoadWord));
        addCandidate(candidates, appendVietnam(cityFallback));
        addCandidate(candidates, appendVietnam(cityFallbackWithoutRoadWord));
        addCandidate(candidates, appendVietnam(streetOnly));
        addCandidate(candidates, appendVietnam(streetOnlyWithoutRoadWord));

        // Cuối cùng mới thử bản không dấu.
        Set<String> current = new LinkedHashSet<>(candidates);
        for (String candidate : current) {
            addCandidate(candidates, removeVietnameseDiacritics(candidate));
        }

        return candidates;
    }

    private String keepStreetAndCity(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }

        String[] parts = address.split(",");
        if (parts.length < 2) {
            return address;
        }

        String street = cleanupSpaces(parts[0]);
        String city = cleanupSpaces(parts[parts.length - 1]);

        if (!StringUtils.hasText(street) || !StringUtils.hasText(city)) {
            return address;
        }

        return street + ", " + city;
    }

    private String keepStreetOnly(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }

        int commaIndex = address.indexOf(',');
        if (commaIndex < 0) {
            return cleanupSpaces(address);
        }
        return cleanupSpaces(address.substring(0, commaIndex));
    }

    private void addCandidate(Set<String> candidates, String value) {
        String cleaned = cleanupSpaces(value);
        if (StringUtils.hasText(cleaned)) {
            candidates.add(cleaned);
        }
    }

    private String appendVietnam(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }

        String normalized = removeVietnameseDiacritics(address).toLowerCase();
        if (normalized.contains("viet nam") || normalized.contains("vietnam")) {
            return address;
        }

        return address + ", Việt Nam";
    }

    private String cleanupSpaces(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ", ");
    }

    private String removeVietnameseDiacritics(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return normalized
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    /**
     * Gọi Pelias một lần cho một candidate.
     * Kết quả hành chính quá chung (city/region/country...) không được dùng làm điểm giao.
     */
    private GeocodeResult tryGeocode(String address, Coordinate focusPoint) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getGeocodeUrl().trim())
                .queryParam("text", address)
                .queryParam("size", 5)
                .queryParam(
                        "boundary.country",
                        StringUtils.hasText(properties.getCountryCode())
                                ? properties.getCountryCode().trim()
                                : "VN")
                .queryParam(
                        "lang",
                        StringUtils.hasText(properties.getLanguageCode())
                                ? properties.getLanguageCode().trim()
                                : "vi");

        if (focusPoint != null) {
            builder.queryParam("focus.point.lon", focusPoint.longitude())
                    .queryParam("focus.point.lat", focusPoint.latitude());
        }

        URI uri = builder
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
            if (features == null || !features.isArray() || features.isEmpty()) {
                return null;
            }

            for (JsonNode feature : features) {
                if (isTooCoarse(feature)) {
                    continue;
                }

                GeocodeResult parsed = parseFeature(feature, address);
                if (parsed != null) {
                    return parsed;
                }
            }

            return null;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể xác định địa chỉ bằng openrouteservice. "
                            + "Vui lòng kiểm tra OPENROUTESERVICE_API_KEY",
                    ex);
        }
    }

    /**
     * Không cho phép một kết quả chỉ là thành phố/quận/tỉnh/quốc gia trở thành
     * tọa độ nhà hàng hoặc tọa độ giao hàng.
     */
    private boolean isTooCoarse(JsonNode feature) {
        JsonNode propertiesNode = feature.path("properties");
        String layer = propertiesNode.path("layer").asText("").trim().toLowerCase();

        if (!StringUtils.hasText(layer)) {
            return false;
        }

        return switch (layer) {
            case "country", "macroregion", "region", "macrocounty", "county",
                    "localadmin", "locality", "borough", "neighbourhood" -> true;
            default -> false;
        };
    }

    private GeocodeResult parseFeature(JsonNode feature, String fallbackAddress) {
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }

        double longitude = coordinates.get(0).asDouble(Double.NaN);
        double latitude = coordinates.get(1).asDouble(Double.NaN);

        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
            return null;
        }

        JsonNode propertiesNode = feature.path("properties");
        String label = propertiesNode.path("label").asText("");
        if (!StringUtils.hasText(label)) {
            label = propertiesNode.path("name").asText("");
        }
        if (!StringUtils.hasText(label)) {
            label = fallbackAddress;
        }

        return new GeocodeResult(
                new Coordinate(longitude, latitude),
                label.trim());
    }

    private ResponseStatusException geocodeNotFound(GeocodeTarget target) {
        if (target == GeocodeTarget.RESTAURANT) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không xác định được vị trí nhà hàng từ địa chỉ trong Cài đặt hệ thống. "
                            + "Vui lòng kiểm tra lại địa chỉ nhà hàng");
        }

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không tìm thấy địa chỉ giao hàng trên bản đồ. "
                        + "Vui lòng nhập đầy đủ số nhà và tên đường");
    }

    private enum GeocodeTarget {
        RESTAURANT,
        DELIVERY
    }

    private record Coordinate(double longitude, double latitude) {
    }

    private record GeocodeResult(
            Coordinate coordinate,
            String formattedAddress) {
    }

    public record RouteResult(
            int distanceMeters,
            long durationSeconds,
            String routeGeometry,
            String resolvedAddress) {
    }
}
