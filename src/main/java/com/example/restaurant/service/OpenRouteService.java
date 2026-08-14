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

            JsonNode distanceNode = summary.path("distance");
            JsonNode durationNode = summary.path("duration");

            if (!distanceNode.isNumber()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dịch vụ bản đồ không trả về khoảng cách hợp lệ");
            }

            double rawDistanceMeters = distanceNode.asDouble();
            if (!Double.isFinite(rawDistanceMeters) || rawDistanceMeters < 0d) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dịch vụ bản đồ không trả về khoảng cách hợp lệ");
            }

            int distanceMeters = (int) Math.round(rawDistanceMeters);
            long durationSeconds = durationNode.isNumber()
                    ? Math.max(0L, Math.round(durationNode.asDouble()))
                    : 0L;

            JsonNode coordinates = feature.path("geometry").path("coordinates");
            boolean geometryValid = coordinates != null
                    && coordinates.isArray()
                    && !coordinates.isEmpty();

            /*
             * openrouteservice đôi khi trả distance = 0 hoặc geometry rỗng khi
             * điểm đầu và điểm cuối rất gần nhau / cùng được snap vào một đoạn đường.
             * Đây không phải lỗi đối với đơn giao ngay cạnh nhà hàng.
             *
             * Chỉ dùng khoảng cách đường chim bay làm fallback cho trường hợp rất gần
             * (<= 500 m). Các tuyến xa hơn vẫn bắt buộc phải có route hợp lệ từ ORS
             * để không làm sai nghiệp vụ tính phí giao hàng.
             */
            if (distanceMeters == 0 || !geometryValid) {
                int directDistanceMeters = (int) Math.round(
                        haversineDistanceMeters(origin, destination.coordinate()));

                if (directDistanceMeters > 500) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Dịch vụ bản đồ không trả về tuyến đường hợp lệ");
                }

                // Tránh trả về 0 m cho hai địa chỉ rất gần hoặc bị snap cùng một điểm.
                distanceMeters = Math.max(1, directDistanceMeters);

                if (!geometryValid) {
                    String fallbackGeometry = buildFallbackGeometry(
                            origin,
                            destination.coordinate());

                    return new RouteResult(
                            distanceMeters,
                            durationSeconds,
                            fallbackGeometry,
                            destination.formattedAddress(),
                            destination.locationContext());
                }
            }

            return new RouteResult(
                    distanceMeters,
                    durationSeconds,
                    coordinates.toString(),
                    destination.formattedAddress(),
                    destination.locationContext());
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
     * SystemSettingService đồng bộ trường này từ bảng cai_dat_he_thong khi khởi
     * động
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
            // Đọc lại trong synchronized để nếu admin vừa cập nhật địa chỉ thì dùng bản mới
            // nhất.
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
     * Với địa chỉ đầy đủ nhưng Pelias chỉ trả kết quả quá chung như "Đà Nẵng, Việt
     * Nam",
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

        String requiredAdministrativeArea = target == GeocodeTarget.DELIVERY
                ? extractAdministrativeArea(address)
                : "";

        Set<String> candidates = buildAddressCandidates(address, target);

        /*
         * Khi khách đã nhập rõ tỉnh/thành thì chính địa danh đó phải là tiêu chí
         * phân giải chính. Không bias về vị trí nhà hàng, nếu không một đường
         * trùng tên ở Đà Nẵng có thể được xếp hạng cao hơn địa chỉ ở Huế.
         */
        Coordinate effectiveFocusPoint = target == GeocodeTarget.DELIVERY
                && StringUtils.hasText(requiredAdministrativeArea)
                        ? null
                        : focusPoint;

        for (String candidate : candidates) {
            GeocodeResult result = tryGeocode(
                    candidate,
                    effectiveFocusPoint,
                    requiredAdministrativeArea);
            if (result != null) {
                return result;
            }
        }

        throw geocodeNotFound(target);
    }

    /**
     * Sinh các biến thể tìm kiếm theo thứ tự ưu tiên, vẫn giữ số nhà.
     */
    private Set<String> buildAddressCandidates(String rawAddress, GeocodeTarget target) {
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

        /*
         * Nếu khách đã nhập tỉnh/thành (địa chỉ có dấu phẩy), không được fallback
         * xuống chỉ còn số nhà + tên đường. Việc bỏ mất tỉnh/thành có thể khiến
         * Pelias chọn một đường trùng tên gần nhà hàng ở địa phương khác.
         *
         * Giữ fallback cũ cho địa chỉ nhà hàng và cho trường hợp khách chỉ nhập
         * một cụm địa chỉ không có phần tỉnh/thành để không làm thay đổi hành vi cũ.
         */
        boolean allowStreetOnlyFallback = target == GeocodeTarget.RESTAURANT
                || !hasExplicitAdministrativeArea(original);
        if (allowStreetOnlyFallback) {
            addCandidate(candidates, streetOnly);
            addCandidate(candidates, streetOnlyWithoutRoadWord);
        }

        addCandidate(candidates, appendVietnam(original));
        addCandidate(candidates, appendVietnam(withoutRoadWord));
        addCandidate(candidates, appendVietnam(cityFallback));
        addCandidate(candidates, appendVietnam(cityFallbackWithoutRoadWord));
        if (allowStreetOnlyFallback) {
            addCandidate(candidates, appendVietnam(streetOnly));
            addCandidate(candidates, appendVietnam(streetOnlyWithoutRoadWord));
        }

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

    private boolean hasExplicitAdministrativeArea(String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }

        String[] parts = address.split(",");
        return parts.length >= 2
                && StringUtils.hasText(cleanupSpaces(parts[parts.length - 1]));
    }

    /**
     * Lấy phần tỉnh/thành mà người dùng nhập ở cuối địa chỉ.
     * Ví dụ: "50A Hùng Vương, Phú Nhuận, Huế" -> "hue".
     */
    private String extractAdministrativeArea(String address) {
        if (!hasExplicitAdministrativeArea(address)) {
            return "";
        }

        String[] parts = address.split(",");
        return normalizeAdministrativeText(parts[parts.length - 1]);
    }

    private boolean matchesAdministrativeArea(
            JsonNode feature,
            String requiredAdministrativeArea) {
        if (!StringUtils.hasText(requiredAdministrativeArea)) {
            return true;
        }

        JsonNode propertiesNode = feature.path("properties");

        StringBuilder administrativeText = new StringBuilder();
        appendAdministrativeValue(administrativeText, propertiesNode.path("label").asText(""));
        appendAdministrativeValue(administrativeText, propertiesNode.path("region").asText(""));
        appendAdministrativeValue(administrativeText, propertiesNode.path("region_a").asText(""));
        appendAdministrativeValue(administrativeText, propertiesNode.path("county").asText(""));
        appendAdministrativeValue(administrativeText, propertiesNode.path("localadmin").asText(""));
        appendAdministrativeValue(administrativeText, propertiesNode.path("locality").asText(""));
        appendAdministrativeValue(administrativeText, propertiesNode.path("borough").asText(""));

        String normalizedFeatureArea = normalizeAdministrativeText(administrativeText.toString());
        return containsAdministrativeArea(
                normalizedFeatureArea,
                requiredAdministrativeArea);
    }

    private void appendAdministrativeValue(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value);
    }

    private boolean containsAdministrativeArea(
            String normalizedFeatureArea,
            String normalizedRequiredArea) {
        if (!StringUtils.hasText(normalizedFeatureArea)
                || !StringUtils.hasText(normalizedRequiredArea)) {
            return false;
        }

        if (normalizedFeatureArea.contains(normalizedRequiredArea)) {
            return true;
        }

        /*
         * Một số cách viết phổ biến có thể khác label của Pelias.
         * Chuẩn hóa riêng TP.HCM để tránh làm địa chỉ hợp lệ bị loại.
         */
        if ("hcm".equals(normalizedRequiredArea)
                || "tphcm".equals(normalizedRequiredArea)
                || "sai gon".equals(normalizedRequiredArea)
                || "saigon".equals(normalizedRequiredArea)) {
            return normalizedFeatureArea.contains("ho chi minh");
        }

        return false;
    }

    private String normalizeAdministrativeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalized = removeVietnameseDiacritics(value)
                .toLowerCase()
                .replaceAll("(?iu)\\b(thanh pho|tp\\.?|tinh|province|city)\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized;
    }

    /**
     * Gọi Pelias một lần cho một candidate.
     * Kết quả hành chính quá chung (city/region/country...) không được dùng làm
     * điểm giao.
     */
    private GeocodeResult tryGeocode(
            String address,
            Coordinate focusPoint,
            String requiredAdministrativeArea) {
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

                /*
                 * Nếu khách đã nhập rõ tỉnh/thành, chỉ chấp nhận feature thuộc
                 * đúng khu vực đó. Ví dụ nhập "... Huế" thì kết quả ở Đà Nẵng
                 * phải bị loại dù nó nằm gần nhà hàng hơn.
                 */
                if (!matchesAdministrativeArea(feature, requiredAdministrativeArea)) {
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
                    "localadmin", "locality", "borough", "neighbourhood" ->
                true;
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

        String locationContext = buildLocationContext(propertiesNode, label);

        return new GeocodeResult(
                new Coordinate(longitude, latitude),
                label.trim(),
                locationContext);
    }

    private String buildLocationContext(JsonNode propertiesNode, String label) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addLocationContext(values, label);
        addLocationContext(values, propertiesNode.path("name").asText(""));
        addLocationContext(values, propertiesNode.path("neighbourhood").asText(""));
        addLocationContext(values, propertiesNode.path("borough").asText(""));
        addLocationContext(values, propertiesNode.path("locality").asText(""));
        addLocationContext(values, propertiesNode.path("localadmin").asText(""));
        addLocationContext(values, propertiesNode.path("county").asText(""));
        addLocationContext(values, propertiesNode.path("region").asText(""));
        addLocationContext(values, propertiesNode.path("macroregion").asText(""));
        return String.join(", ", values);
    }

    private void addLocationContext(Set<String> values, String value) {
        String cleaned = cleanupSpaces(value);
        if (StringUtils.hasText(cleaned)) {
            values.add(cleaned);
        }
    }

    /**
     * Tính khoảng cách đường chim bay giữa hai tọa độ.
     * Chỉ dùng làm fallback cho trường hợp hai điểm rất gần nhau khi ORS trả 0 m
     * hoặc không có geometry.
     */
    private double haversineDistanceMeters(Coordinate from, Coordinate to) {
        final double earthRadiusMeters = 6_371_000d;

        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(lat1) * Math.cos(lat2)
                        * Math.sin(deltaLon / 2d) * Math.sin(deltaLon / 2d);

        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return earthRadiusMeters * c;
    }

    /**
     * Geometry tối thiểu để frontend vẫn có dữ liệu tuyến khi hai điểm rất gần
     * nhưng ORS không trả geometry.
     */
    private String buildFallbackGeometry(Coordinate from, Coordinate to) {
        return "[["
                + from.longitude() + "," + from.latitude()
                + "],["
                + to.longitude() + "," + to.latitude()
                + "]]";
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
            String formattedAddress,
            String locationContext) {
    }

    public record RouteResult(
            int distanceMeters,
            long durationSeconds,
            String routeGeometry,
            String resolvedAddress,
            String resolvedLocationContext) {
    }
}