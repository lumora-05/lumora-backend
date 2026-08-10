package com.example.restaurant.service;

import com.example.restaurant.config.OpenRouteServiceProperties;
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Địa chỉ giao hàng không hợp lệ");
        }

        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "openrouteservice chưa được cấu hình ở backend");
        }

        // Xác định vị trí nhà hàng trước. Nếu lỗi phải báo đúng là lỗi vị trí nhà hàng,
        // không được làm người dùng hiểu nhầm địa chỉ giao hàng bị sai.
        Coordinate origin = originCoordinate();

        // Ưu tiên kết quả geocode gần nhà hàng để giảm khả năng chọn nhầm địa danh
        // trùng tên ở tỉnh/thành khác.
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
     * Tọa độ nhà hàng được cache để không phải geocode lại sau mỗi lần khách nhập
     * địa chỉ.
     */
    private Coordinate originCoordinate() {
        Coordinate cached = originCache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (originCache == null) {
                GeocodeResult origin = geocodeWithFallback(
                        properties.getOriginAddress(),
                        null,
                        GeocodeTarget.RESTAURANT);
                originCache = origin.coordinate();
            }
            return originCache;
        }
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
     * Quan trọng:
     * - Không bỏ số nhà, vì bỏ số nhà có thể khiến phí giao bị tính từ giữa con
     * đường.
     * - Chỉ chuẩn hóa các phần dễ làm Pelias khó nhận diện như từ "Đường",
     * hậu tố Việt Nam và dấu tiếng Việt.
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
     * Sinh các biến thể tìm kiếm theo thứ tự ưu tiên.
     */
    private Set<String> buildAddressCandidates(String rawAddress) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        String original = cleanupSpaces(rawAddress);
        addCandidate(candidates, original);

        // Ví dụ:
        // "137 Đường Nguyễn Thị Thập, Thanh Khê, Đà Nẵng"
        // -> "137 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng"
        String withoutRoadWord = cleanupSpaces(
                original.replaceAll("(?iu)\\bđường\\s+", ""));
        addCandidate(candidates, withoutRoadWord);

        // Thêm quốc gia để Pelias có thêm ngữ cảnh.
        addCandidate(candidates, appendVietnam(original));
        addCandidate(candidates, appendVietnam(withoutRoadWord));

        // Cuối cùng mới thử bản không dấu, vẫn giữ nguyên số nhà và khu vực.
        String ascii = removeVietnameseDiacritics(original);
        String asciiWithoutRoadWord = removeVietnameseDiacritics(withoutRoadWord);

        addCandidate(candidates, ascii);
        addCandidate(candidates, asciiWithoutRoadWord);
        addCandidate(candidates, appendVietnam(ascii));
        addCandidate(candidates, appendVietnam(asciiWithoutRoadWord));

        return candidates;
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

        // Chữ đ/Đ không bị loại bởi NFD.
        return normalized
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    /**
     * Gọi Pelias một lần cho một candidate.
     * Trả null nếu không có kết quả để caller thử candidate tiếp theo.
     */
    private GeocodeResult tryGeocode(String address, Coordinate focusPoint) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getGeocodeUrl().trim())
                .queryParam("text", address)
                // Lấy vài kết quả thay vì đúng 1 kết quả để Pelias có khả năng
                // xếp hạng tốt hơn khi có focus point.
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

            // Pelias đã xếp hạng kết quả; focus point giúp ưu tiên địa điểm gần nhà hàng.
            for (JsonNode feature : features) {
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
                    "Không xác định được vị trí nhà hàng trên bản đồ. "
                            + "Vui lòng kiểm tra app.open-route-service.origin-address");
        }

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không tìm thấy địa chỉ giao hàng trên bản đồ. "
                        + "Vui lòng nhập đầy đủ số nhà, tên đường, phường/xã và tỉnh/thành phố");
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
