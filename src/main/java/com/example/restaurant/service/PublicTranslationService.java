package com.example.restaurant.service;

import com.example.restaurant.config.ChatbotAiProperties;
import com.example.restaurant.dto.translation.PublicMenuTranslationResponse;
import com.example.restaurant.entity.Category;
import com.example.restaurant.entity.Food;
import com.example.restaurant.repository.CategoryRepository;
import com.example.restaurant.repository.FoodRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicTranslationService {
    private static final Logger log = LoggerFactory.getLogger(PublicTranslationService.class);
    private static final int AI_BATCH_SIZE = 80;
    private static final int MAX_CACHE_ENTRIES = 5000;

    private static final String SYSTEM_PROMPT = """
            You translate public restaurant menu content for LUMORA from Vietnamese into natural English.
            Translate only the supplied text. Do not add facts, ingredients, prices, claims, or marketing details.
            For FOOD_NAME and CATEGORY_NAME, use concise menu-style English. Keep internationally standard dish names
            such as Panna Cotta, Tiramisu, Carbonara, Pizza Margherita, Caesar Salad, and Risotto when appropriate.
            For descriptions, preserve the original meaning and tone without embellishment.
            Brand names and proper nouns must be preserved unless a conventional English form clearly exists.
            Return exactly one result for each supplied key and do not change any key.
            """;

    private final ChatbotAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final FoodRepository foodRepository;
    private final CategoryRepository categoryRepository;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final boolean enabled;

    public PublicTranslationService(ChatbotAiProperties aiProperties,
                                    ObjectMapper objectMapper,
                                    FoodRepository foodRepository,
                                    CategoryRepository categoryRepository,
                                    @Value("${app.translation.ai.enabled:true}") boolean enabled) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.foodRepository = foodRepository;
        this.categoryRepository = categoryRepository;
        this.enabled = enabled;
    }

    public PublicMenuTranslationResponse translateMenu(String requestedLanguage) {
        String targetLanguage = normalizeLanguage(requestedLanguage);
        List<Food> foods = foodRepository.findByTrangThaiTrue();
        List<Category> categories = categoryRepository.findByTrangThaiTrue();

        if (!"en".equals(targetLanguage)) {
            return originalResponse(targetLanguage, foods, categories);
        }

        List<TranslationUnit> units = new ArrayList<>();
        for (Food food : foods) {
            addUnit(units, "food:" + food.getMaMonAn() + ":name", "FOOD_NAME", food.getTenMonAn());
            addUnit(units, "food:" + food.getMaMonAn() + ":description", "FOOD_DESCRIPTION", food.getMoTa());
        }
        for (Category category : categories) {
            addUnit(units, "category:" + category.getMaDanhMuc() + ":name", "CATEGORY_NAME", category.getTenDanhMuc());
            addUnit(units, "category:" + category.getMaDanhMuc() + ":description", "CATEGORY_DESCRIPTION", category.getMoTa());
        }

        Map<String, String> translatedByKey = translateUnits(units, targetLanguage);

        List<PublicMenuTranslationResponse.FoodItem> foodItems = foods.stream()
                .map(food -> new PublicMenuTranslationResponse.FoodItem(
                        food.getMaMonAn(),
                        translatedByKey.get("food:" + food.getMaMonAn() + ":name"),
                        translatedByKey.get("food:" + food.getMaMonAn() + ":description")
                ))
                .toList();

        List<PublicMenuTranslationResponse.CategoryItem> categoryItems = categories.stream()
                .map(category -> new PublicMenuTranslationResponse.CategoryItem(
                        category.getMaDanhMuc(),
                        translatedByKey.get("category:" + category.getMaDanhMuc() + ":name"),
                        translatedByKey.get("category:" + category.getMaDanhMuc() + ":description")
                ))
                .toList();

        return new PublicMenuTranslationResponse(targetLanguage, foodItems, categoryItems);
    }

    private PublicMenuTranslationResponse originalResponse(String language,
                                                           List<Food> foods,
                                                           List<Category> categories) {
        return new PublicMenuTranslationResponse(
                language,
                foods.stream().map(food -> new PublicMenuTranslationResponse.FoodItem(
                        food.getMaMonAn(), food.getTenMonAn(), food.getMoTa())).toList(),
                categories.stream().map(category -> new PublicMenuTranslationResponse.CategoryItem(
                        category.getMaDanhMuc(), category.getTenDanhMuc(), category.getMoTa())).toList()
        );
    }

    private Map<String, String> translateUnits(List<TranslationUnit> units, String targetLanguage) {
        Map<String, String> result = new LinkedHashMap<>();
        List<TranslationUnit> missing = new ArrayList<>();

        for (TranslationUnit unit : units) {
            String cached = cache.get(cacheKey(targetLanguage, unit.kind(), unit.text()));
            if (StringUtils.hasText(cached)) {
                result.put(unit.key(), cached);
            } else {
                missing.add(unit);
            }
        }

        if (isReady()) {
            for (int start = 0; start < missing.size(); start += AI_BATCH_SIZE) {
                List<TranslationUnit> batch = missing.subList(start, Math.min(start + AI_BATCH_SIZE, missing.size()));
                Map<String, String> translated = translateBatch(batch, targetLanguage);
                for (TranslationUnit unit : batch) {
                    String value = translated.get(unit.key());
                    if (!StringUtils.hasText(value)) continue;
                    putCache(cacheKey(targetLanguage, unit.kind(), unit.text()), value);
                    result.put(unit.key(), value);
                }
            }
        }

        return result;
    }

    private Map<String, String> translateBatch(List<TranslationUnit> items, String targetLanguage) {
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("targetLanguage", targetLanguage);
            context.put("items", items.stream().map(item -> Map.of(
                    "key", item.key(),
                    "kind", item.kind(),
                    "text", item.text()
            )).toList());

            Map<String, Object> itemSchema = new LinkedHashMap<>();
            itemSchema.put("type", "object");
            itemSchema.put("properties", Map.of(
                    "key", Map.of("type", "string"),
                    "text", Map.of("type", "string")
            ));
            itemSchema.put("required", List.of("key", "text"));
            itemSchema.put("additionalProperties", false);

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of(
                    "translations", Map.of("type", "array", "items", itemSchema)
            ));
            schema.put("required", List.of("translations"));
            schema.put("additionalProperties", false);

            Map<String, Object> format = new LinkedHashMap<>();
            format.put("type", "json_schema");
            format.put("name", "lumora_public_menu_translation");
            format.put("strict", true);
            format.put("schema", schema);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", aiProperties.getModel());
            requestBody.put("store", false);
            requestBody.put("input", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", objectMapper.writeValueAsString(context))
            ));
            requestBody.put("max_output_tokens", Math.max(1000, Math.min(5000, items.size() * 180)));
            requestBody.put("text", Map.of("format", format));

            String responseBody = createClient()
                    .post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) return Map.of();
            return parseTranslations(responseBody, items);
        } catch (RestClientResponseException exception) {
            log.warn("Public menu translation request failed with status {}: {}",
                    exception.getStatusCode().value(), compact(exception.getResponseBodyAsString()));
        } catch (ResourceAccessException exception) {
            log.warn("Public menu translation request timed out or could not connect: {}", exception.getMessage());
        } catch (Exception exception) {
            log.warn("Public menu translation failed: {}", exception.getMessage());
        }
        return Map.of();
    }

    private Map<String, String> parseTranslations(String responseBody,
                                                  List<TranslationUnit> requestedItems) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if ("incomplete".equalsIgnoreCase(root.path("status").asText())) return Map.of();

        String outputText = null;
        JsonNode output = root.path("output");
        if (output.isArray()) {
            outer:
            for (JsonNode outputItem : output) {
                JsonNode content = outputItem.path("content");
                if (!content.isArray()) continue;
                for (JsonNode contentItem : content) {
                    if ("output_text".equals(contentItem.path("type").asText()) && contentItem.hasNonNull("text")) {
                        outputText = contentItem.path("text").asText();
                        break outer;
                    }
                }
            }
        }
        if (!StringUtils.hasText(outputText)) return Map.of();

        Set<String> allowedKeys = requestedItems.stream().map(TranslationUnit::key)
                .collect(java.util.stream.Collectors.toSet());
        JsonNode translations = objectMapper.readTree(outputText).path("translations");
        if (!translations.isArray()) return Map.of();

        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode item : translations) {
            String key = item.path("key").asText();
            String text = item.path("text").asText();
            if (allowedKeys.contains(key) && StringUtils.hasText(text)) {
                result.putIfAbsent(key, text.trim());
            }
        }
        return result;
    }

    private RestClient createClient() {
        Duration timeout = Duration.ofSeconds(Math.max(3, aiProperties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        String baseUrl = aiProperties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) baseUrl = "https://api.openai.com/v1";
        baseUrl = baseUrl.replaceAll("/+$", "");

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey().trim())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private void addUnit(List<TranslationUnit> units, String key, String kind, String text) {
        if (StringUtils.hasText(text)) units.add(new TranslationUnit(key, kind, text.trim()));
    }

    private boolean isReady() {
        return enabled
                && StringUtils.hasText(aiProperties.getApiKey())
                && StringUtils.hasText(aiProperties.getModel());
    }

    private String normalizeLanguage(String language) {
        return String.valueOf(language).toLowerCase().startsWith("en") ? "en" : "vi";
    }

    private String cacheKey(String language, String kind, String text) {
        return language + '\u0000' + kind + '\u0000' + text.trim();
    }

    private void putCache(String key, String value) {
        if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
        cache.put(key, value);
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private record TranslationUnit(String key, String kind, String text) {
    }
}
