package com.example.restaurant.service;

import com.example.restaurant.config.RestaurantInfoProperties;
import com.example.restaurant.dto.chatbot.*;
import com.example.restaurant.entity.ChatMessage;
import com.example.restaurant.entity.ChatSession;
import com.example.restaurant.entity.Food;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.entity.Promotion;
import com.example.restaurant.repository.ChatMessageRepository;
import com.example.restaurant.repository.ChatSessionRepository;
import com.example.restaurant.repository.FoodRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatbotService {
    private static final int MAX_FOOD_RESULTS = 4;
    private static final int MAX_RANKED_FOOD_RESULTS = 10;
    private static final int MAX_PROMOTION_RESULTS = 4;

    private static final List<String> DEFAULT_QUICK_REPLIES = List.of(
            "Gợi ý món cho tôi",
            "Có món nào dưới 200.000đ?",
            "Ưu đãi hiện tại",
            "Nhà hàng mở cửa lúc nào?",
            "Tôi muốn đặt bàn"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "co", "khong", "mon", "nao", "cho", "toi", "minh", "ban", "nha", "hang",
            "giup", "tim", "goi", "y", "an", "muon", "can", "duoi", "tren", "khoang",
            "gia", "giá", "vnd", "dong", "nghin", "ngan", "trieu", "nguoi", "phu", "hop",
            "voi", "va", "hoac", "la", "mot", "vai", "cac", "dang", "hien", "tai"
    );

    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(?<![\\p{L}\\d])(\\d+(?:[.,]\\d{1,3})*(?:[.,]\\d+)?)\\s*(trieu|m|nghin|ngan|k|d|dong)?(?![\\p{L}\\d])"
    );

    private static final Pattern NUMERIC_FOOD_COUNT = Pattern.compile(
            "(?:\\btop\\s*)?(\\d{1,2})\\s+mon\\b"
    );

    private static final Map<String, Integer> VIETNAMESE_NUMBER_WORDS = Map.ofEntries(
            Map.entry("mot", 1),
            Map.entry("hai", 2),
            Map.entry("ba", 3),
            Map.entry("bon", 4),
            Map.entry("tu", 4),
            Map.entry("nam", 5),
            Map.entry("sau", 6),
            Map.entry("bay", 7),
            Map.entry("tam", 8),
            Map.entry("chin", 9),
            Map.entry("muoi", 10)
    );

    private static final Map<String, String> ORDER_STATUS_LABELS = Map.ofEntries(
            Map.entry("CHO_XAC_NHAN", "Chờ nhân viên xác nhận"),
            Map.entry("DA_XAC_NHAN", "Đã xác nhận"),
            Map.entry("DANG_CHUAN_BI", "Đang chuẩn bị"),
            Map.entry("DANG_CHE_BIEN", "Bếp đang chế biến"),
            Map.entry("SAN_SANG", "Món đã sẵn sàng"),
            Map.entry("SAN_SANG_PHUC_VU", "Sẵn sàng phục vụ"),
            Map.entry("DA_HOAN_THANH", "Đã hoàn thành"),
            Map.entry("DA_PHUC_VU", "Đã phục vụ"),
            Map.entry("CHO_THANH_TOAN", "Đang chờ thanh toán"),
            Map.entry("SAN_SANG_THANH_TOAN", "Sẵn sàng thanh toán"),
            Map.entry("DA_THANH_TOAN", "Đã thanh toán"),
            Map.entry("DA_HUY", "Đã hủy")
    );

    private final FoodRepository foodRepository;
    private final PromotionService promotionService;
    private final TableService tableService;
    private final OrderService orderService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RestaurantInfoProperties restaurantInfo;
    private final ObjectMapper objectMapper;
    private final ChatbotAiService chatbotAiService;

    public ChatbotService(FoodRepository foodRepository,
                          PromotionService promotionService,
                          TableService tableService,
                          OrderService orderService,
                          ChatSessionRepository chatSessionRepository,
                          ChatMessageRepository chatMessageRepository,
                          RestaurantInfoProperties restaurantInfo,
                          ObjectMapper objectMapper,
                          ChatbotAiService chatbotAiService) {
        this.foodRepository = foodRepository;
        this.promotionService = promotionService;
        this.tableService = tableService;
        this.orderService = orderService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.restaurantInfo = restaurantInfo;
        this.objectMapper = objectMapper;
        this.chatbotAiService = chatbotAiService;
    }

    @Transactional
    public ChatbotResponse reply(ChatbotMessageRequest request) {
        ChatSession session = resolveSession(request.sessionId(), request.qrToken());
        List<ChatMessage> recentMessages = recentMessages(session);
        String userMessage = request.message().trim();
        saveMessage(session, "USER", userMessage, null, null);

        AiChatDecision aiDecision = chatbotAiService
                .analyze(userMessage, recentMessages, StringUtils.hasText(session.getQrToken()))
                .filter(this::isUsableAiDecision)
                .orElse(null);

        ChatbotResponse response = buildResponse(session, userMessage, aiDecision);
        saveMessage(session, "ASSISTANT", response.message(), response.intent(),
                serializeMetadata(response, aiDecision));

        session.setLastActivityAt(LocalDateTime.now());
        chatSessionRepository.save(session);
        return response;
    }

    public List<String> quickReplies() {
        return DEFAULT_QUICK_REPLIES;
    }

    private ChatbotResponse buildResponse(ChatSession session,
                                           String rawMessage,
                                           AiChatDecision aiDecision) {
        String normalized = normalize(rawMessage);
        Intent intent = resolveIntent(normalized, aiDecision);

        if (aiDecision != null
                && aiDecision.clarificationNeeded()
                && StringUtils.hasText(aiDecision.clarificationQuestion())
                && (intent == Intent.FOOD_RECOMMENDATION || intent == Intent.UNKNOWN)) {
            return baseResponse(
                    session,
                    intent,
                    aiDecision.clarificationQuestion(),
                    List.of(), List.of(), null,
                    List.of(ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl())),
                    aiSafetyDisclaimer(aiDecision, normalized)
            );
        }

        return switch (intent) {
            case GREETING -> baseResponse(
                    session,
                    intent,
                    aiMessageOr(aiDecision,
                            "Xin chào! Tôi là trợ lý LUMORA. Tôi có thể giúp bạn tìm món, xem ưu đãi, "
                                    + "hướng dẫn đặt bàn hoặc tra cứu trạng thái đơn tại bàn."),
                    List.of(), List.of(), null,
                    List.of(
                            ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl()),
                            ChatbotActionResponse.link("Đặt bàn", "OPEN_RESERVATION", restaurantInfo.getReservationUrl())
                    ),
                    null
            );
            case OPENING_HOURS -> openingHoursResponse(session, intent);
            case CONTACT_INFO -> contactResponse(session, intent);
            case RESERVATION_SUPPORT -> reservationResponse(session, intent);
            case PROMOTION_SEARCH -> promotionResponse(session, intent);
            case FOOD_SEARCH, FOOD_RECOMMENDATION -> foodResponse(session, intent, normalized, aiDecision);
            case ORDER_STATUS -> orderStatusResponse(session, intent);
            case CALL_WAITER -> callWaiterResponse(session, intent);
            case PAYMENT_SUPPORT -> paymentResponse(session, intent);
            case ALLERGY_SAFETY -> allergyResponse(session, intent);
            case UNKNOWN -> baseResponse(
                    session,
                    intent,
                    aiMessageOr(aiDecision,
                            "Tôi chưa hiểu rõ yêu cầu này. Bạn có thể hỏi về món ăn, mức giá, ưu đãi, "
                                    + "giờ mở cửa, đặt bàn hoặc trạng thái đơn hàng."),
                    List.of(), List.of(), null,
                    List.of(
                            ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl()),
                            ChatbotActionResponse.link("Đặt bàn", "OPEN_RESERVATION", restaurantInfo.getReservationUrl())
                    ),
                    null
            );
        };
    }

    private ChatbotResponse openingHoursResponse(ChatSession session, Intent intent) {
        String message = isConfigured(restaurantInfo.getOpeningHours())
                ? restaurantInfo.getName() + " phục vụ theo khung giờ: " + restaurantInfo.getOpeningHours() + "."
                : "Giờ mở cửa của nhà hàng chưa được cấu hình trên hệ thống. Vui lòng liên hệ nhà hàng để được xác nhận.";
        return baseResponse(session, intent, message, List.of(), List.of(), null, contactActions(), null);
    }

    private ChatbotResponse contactResponse(ChatSession session, Intent intent) {
        List<String> parts = new ArrayList<>();
        if (isConfigured(restaurantInfo.getAddress())) {
            parts.add("Địa chỉ: " + restaurantInfo.getAddress());
        }
        if (isConfigured(restaurantInfo.getPhone())) {
            parts.add("Điện thoại: " + restaurantInfo.getPhone());
        }
        if (isConfigured(restaurantInfo.getEmail())) {
            parts.add("Email: " + restaurantInfo.getEmail());
        }
        String message = parts.isEmpty()
                ? "Thông tin liên hệ của nhà hàng chưa được cấu hình. Vui lòng sử dụng mục Liên hệ trên trang chủ."
                : String.join(" · ", parts) + ".";
        return baseResponse(session, intent, message, List.of(), List.of(), null, contactActions(), null);
    }

    private ChatbotResponse reservationResponse(ChatSession session, Intent intent) {
        return baseResponse(
                session,
                intent,
                "Bạn có thể đặt bàn trực tuyến bằng cách chọn ngày, giờ, số lượng khách và nhập thông tin liên hệ. "
                        + "Yêu cầu chỉ được xác nhận sau khi nhà hàng tiếp nhận thành công.",
                List.of(), List.of(), null,
                List.of(ChatbotActionResponse.link("Đặt bàn ngay", "OPEN_RESERVATION", restaurantInfo.getReservationUrl())),
                null
        );
    }

    private ChatbotResponse promotionResponse(ChatSession session, Intent intent) {
        List<Promotion> activePromotions = promotionService.findActiveNow().stream()
                .limit(MAX_PROMOTION_RESULTS)
                .toList();
        if (activePromotions.isEmpty()) {
            return baseResponse(
                    session,
                    intent,
                    "Hiện chưa có chương trình khuyến mãi đang áp dụng trên hệ thống.",
                    List.of(), List.of(), null,
                    List.of(ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl())),
                    null
            );
        }

        List<ChatbotPromotionResponse> promotions = activePromotions.stream()
                .map(this::toPromotionResponse)
                .toList();
        return baseResponse(
                session,
                intent,
                "Hiện có " + promotions.size() + " chương trình ưu đãi đang áp dụng. "
                        + "Điều kiện chi tiết được hiển thị trong từng ưu đãi.",
                List.of(), promotions, null,
                List.of(ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl())),
                null
        );
    }

    private ChatbotResponse foodResponse(ChatSession session,
                                         Intent intent,
                                         String normalizedMessage,
                                         AiChatDecision aiDecision) {
        PriceOrder priceOrder = resolvePriceOrder(normalizedMessage, aiDecision);
        int resultLimit = resolveResultLimit(normalizedMessage, aiDecision, priceOrder);
        String enrichedQuery = buildFoodQuery(normalizedMessage, aiDecision);
        Optional<BigDecimal> detectedBudget = aiDecision != null && aiDecision.hasBudget()
                ? Optional.of(aiDecision.budget())
                : extractBudget(normalizedMessage);
        BudgetScope budgetScope = resolveBudgetScope(intent, aiDecision);
        BigDecimal perItemLimit = budgetScope == BudgetScope.TOTAL ? null : detectedBudget.orElse(null);

        List<Food> activeFoods = foodRepository.findByTrangThaiTrue();
        List<Food> matched = filterFoods(activeFoods, enrichedQuery, perItemLimit);
        matched = applyAiExclusions(matched, aiDecision);

        List<Food> selected;
        if (priceOrder != PriceOrder.NONE) {
            selected = selectRankedFoods(matched, priceOrder, resultLimit);
        } else if (budgetScope == BudgetScope.TOTAL && detectedBudget.isPresent()) {
            selected = selectWithinTotalBudget(matched, detectedBudget.get(), MAX_FOOD_RESULTS);
        } else {
            selected = selectDiverseFoods(matched, MAX_FOOD_RESULTS);
        }

        if (selected.isEmpty()) {
            String pricePart = detectedBudget.map(value -> " trong mức " + formatMoney(value)).orElse("");
            return baseResponse(
                    session,
                    intent,
                    "Tôi chưa tìm thấy món đang bán phù hợp" + pricePart
                            + ". Bạn có thể thử tên món, danh mục hoặc mức giá khác.",
                    List.of(), List.of(), null,
                    List.of(ChatbotActionResponse.link("Xem toàn bộ thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl())),
                    aiSafetyDisclaimer(aiDecision, enrichedQuery)
            );
        }

        List<ChatbotFoodResponse> foods = selected.stream().map(this::toFoodResponse).toList();
        BigDecimal selectedTotal = selected.stream()
                .map(Food::getGia)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String message;
        if (priceOrder != PriceOrder.NONE) {
            message = rankedFoodMessage(selected, priceOrder);
        } else if (intent == Intent.FOOD_RECOMMENDATION
                && budgetScope == BudgetScope.TOTAL
                && detectedBudget.isPresent()) {
            String guestText = aiDecision != null && aiDecision.hasGuestCount()
                    ? " cho " + aiDecision.guestCount() + " người"
                    : "";
            message = "Dựa trên yêu cầu" + guestText + ", tôi gợi ý " + foods.size()
                    + " món đang bán. Tổng giá niêm yết tạm tính " + formatMoney(selectedTotal)
                    + ", trong ngân sách " + formatMoney(detectedBudget.get()) + ".";
        } else {
            String budgetText = detectedBudget
                    .map(value -> " trong mức tối đa " + formatMoney(value) + " mỗi món")
                    .orElse("");
            message = intent == Intent.FOOD_RECOMMENDATION
                    ? "Tôi gợi ý " + foods.size() + " món đang bán" + budgetText + "."
                    : "Tôi tìm thấy " + foods.size() + " món phù hợp" + budgetText + ".";
        }

        return baseResponse(
                session,
                intent,
                message,
                foods, List.of(), null,
                List.of(ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl())),
                aiSafetyDisclaimer(aiDecision, enrichedQuery)
        );
    }

    private ChatbotResponse orderStatusResponse(ChatSession session, Intent intent) {
        String qrToken = session.getQrToken();
        if (!StringUtils.hasText(qrToken)) {
            return baseResponse(
                    session,
                    intent,
                    "Để kiểm tra đơn hàng, vui lòng mở chatbot từ trang khách hàng sau khi quét mã QR tại bàn.",
                    List.of(), List.of(), null,
                    List.of(ChatbotActionResponse.of("Quét QR tại bàn", "REQUIRE_TABLE_QR")),
                    null
            );
        }

        try {
            Integer tableId = tableService.findCustomerAccessibleTableByToken(qrToken).getMaBan();
            Order order = orderService.findCurrentOrderByTable(tableId);
            ChatbotOrderResponse orderResponse = toOrderResponse(order);
            return baseResponse(
                    session,
                    intent,
                    "Đơn #" + order.getMaDonHang() + " hiện ở trạng thái: " + orderResponse.statusLabel() + ".",
                    List.of(), List.of(), orderResponse,
                    List.of(ChatbotActionResponse.of("Xem chi tiết đơn", "OPEN_CURRENT_ORDER")),
                    null
            );
        } catch (ResponseStatusException exception) {
            String message = exception.getStatusCode() == HttpStatus.NOT_FOUND
                    ? "Bàn hiện chưa có đơn hàng đang phục vụ."
                    : "Không thể xác thực phiên bàn hiện tại. Vui lòng quét lại mã QR hoặc gọi nhân viên hỗ trợ.";
            return baseResponse(session, intent, message, List.of(), List.of(), null, List.of(), null);
        }
    }

    private ChatbotResponse callWaiterResponse(ChatSession session, Intent intent) {
        if (!StringUtils.hasText(session.getQrToken())) {
            return baseResponse(
                    session,
                    intent,
                    "Chức năng gọi nhân viên chỉ khả dụng khi bạn truy cập từ mã QR của bàn.",
                    List.of(), List.of(), null,
                    List.of(ChatbotActionResponse.of("Quét QR tại bàn", "REQUIRE_TABLE_QR")),
                    null
            );
        }
        return baseResponse(
                session,
                intent,
                "Tôi có thể chuyển bạn đến chức năng gọi phục vụ. Hệ thống sẽ chỉ gửi yêu cầu sau khi bạn xác nhận trên giao diện.",
                List.of(), List.of(), null,
                List.of(ChatbotActionResponse.of("Gọi nhân viên", "OPEN_SERVICE_REQUEST")),
                null
        );
    }

    private ChatbotResponse paymentResponse(ChatSession session, Intent intent) {
        if (!StringUtils.hasText(session.getQrToken())) {
            return baseResponse(
                    session,
                    intent,
                    "Yêu cầu thanh toán chỉ khả dụng trong phiên bàn sau khi quét mã QR.",
                    List.of(), List.of(), null,
                    List.of(ChatbotActionResponse.of("Quét QR tại bàn", "REQUIRE_TABLE_QR")),
                    null
            );
        }
        return baseResponse(
                session,
                intent,
                "Bạn có thể mở đơn hàng hiện tại và chọn “Yêu cầu thanh toán”. "
                        + "Hệ thống sẽ gửi thông báo đến thu ngân sau khi bạn xác nhận.",
                List.of(), List.of(), null,
                List.of(ChatbotActionResponse.of("Mở đơn hàng", "OPEN_CURRENT_ORDER")),
                null
        );
    }

    private ChatbotResponse allergyResponse(ChatSession session, Intent intent) {
        return baseResponse(
                session,
                intent,
                "Tôi không thể xác nhận một món hoàn toàn an toàn đối với dị ứng hoặc chế độ ăn đặc biệt chỉ từ mô tả trên hệ thống. "
                        + "Vui lòng báo rõ nguyên liệu cần tránh cho nhân viên trước khi gọi món.",
                List.of(), List.of(), null,
                List.of(
                        ChatbotActionResponse.link("Xem thực đơn", "OPEN_MENU", restaurantInfo.getMenuUrl()),
                        ChatbotActionResponse.of("Gọi nhân viên", "OPEN_SERVICE_REQUEST")
                ),
                "Thông tin nguyên liệu chỉ mang tính tham khảo; nguy cơ nhiễm chéo cần được xác nhận trực tiếp với nhà hàng."
        );
    }

    private ChatbotResponse baseResponse(ChatSession session,
                                         Intent intent,
                                         String message,
                                         List<ChatbotFoodResponse> foods,
                                         List<ChatbotPromotionResponse> promotions,
                                         ChatbotOrderResponse order,
                                         List<ChatbotActionResponse> actions,
                                         String disclaimer) {
        return new ChatbotResponse(
                session.getSessionToken(),
                intent.name(),
                message,
                foods,
                promotions,
                order,
                actions,
                DEFAULT_QUICK_REPLIES,
                disclaimer
        );
    }

    private List<ChatMessage> recentMessages(ChatSession session) {
        int limit = chatbotAiService.maxHistoryMessages();
        if (limit <= 0 || session.getId() == null) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findBySessionOrderByCreatedAtDesc(session, PageRequest.of(0, limit))
        );
        Collections.reverse(messages);
        return messages;
    }

    private boolean isUsableAiDecision(AiChatDecision decision) {
        if (decision == null || decision.confidence() < chatbotAiService.minimumConfidence()) {
            return false;
        }
        try {
            Intent.valueOf(decision.intent().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Intent resolveIntent(String normalizedMessage, AiChatDecision aiDecision) {
        if (detectPriceOrder(normalizedMessage) != PriceOrder.NONE) {
            return Intent.FOOD_SEARCH;
        }
        if (aiDecision != null) {
            try {
                return Intent.valueOf(aiDecision.intent().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Tự động quay về bộ nhận diện theo luật hiện có.
            }
        }
        return detectIntent(normalizedMessage);
    }

    private String aiMessageOr(AiChatDecision aiDecision, String fallback) {
        if (aiDecision != null && StringUtils.hasText(aiDecision.assistantMessage())) {
            return aiDecision.assistantMessage();
        }
        return fallback;
    }

    private String buildFoodQuery(String normalizedMessage, AiChatDecision aiDecision) {
        String positiveMessage = normalizedMessage;
        if (aiDecision != null) {
            for (String exclusion : aiDecision.exclusions()) {
                String normalizedExclusion = normalize(exclusion)
                        .replaceFirst("^(khong|tranh|khong dung|di ung voi)\\s+", "");
                if (StringUtils.hasText(normalizedExclusion)) {
                    positiveMessage = positiveMessage
                            .replace("khong " + normalizedExclusion, " ")
                            .replace(normalizedExclusion, " ");
                }
            }
        }

        List<String> parts = new ArrayList<>();
        parts.add(positiveMessage);
        if (aiDecision != null) {
            parts.addAll(aiDecision.foodKeywords());
            parts.addAll(aiDecision.preferences());
        }
        return sanitizeFoodQuery(normalize(String.join(" ", parts)));
    }

    private String sanitizeFoodQuery(String query) {
        String sanitized = " " + normalize(query) + " ";
        sanitized = sanitized
                .replaceAll("\\b(?:gia\\s+)?cao\\s+nhat\\b", " ")
                .replaceAll("\\b(?:gia\\s+)?thap\\s+nhat\\b", " ")
                .replaceAll("\\b(?:dat|mac)\\s+nhat\\b", " ")
                .replaceAll("\\bre\\s+nhat\\b", " ")
                .replaceAll("\\btop\\s+(?:\\d{1,2}|mot|hai|ba|bon|tu|nam|sau|bay|tam|chin|muoi)\\s+mon\\b", " ")
                .replaceAll("\\b(?:\\d{1,2}|mot|hai|ba|bon|tu|nam|sau|bay|tam|chin|muoi)\\s+mon\\b", " ");
        return sanitized.replaceAll("\\s+", " ").trim();
    }

    private BudgetScope resolveBudgetScope(Intent intent, AiChatDecision aiDecision) {
        if (aiDecision != null && StringUtils.hasText(aiDecision.budgetScope())) {
            try {
                BudgetScope parsed = BudgetScope.valueOf(aiDecision.budgetScope().toUpperCase(Locale.ROOT));
                if (parsed != BudgetScope.UNKNOWN) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // Dùng quy tắc mặc định bên dưới.
            }
        }
        return intent == Intent.FOOD_RECOMMENDATION ? BudgetScope.TOTAL : BudgetScope.PER_ITEM;
    }

    private List<Food> applyAiExclusions(List<Food> foods, AiChatDecision aiDecision) {
        if (aiDecision == null || aiDecision.exclusions().isEmpty()) {
            return foods;
        }
        List<String> exclusions = aiDecision.exclusions().stream()
                .map(this::normalize)
                .map(value -> value.replaceFirst("^(khong|tranh|khong dung|di ung voi)\\s+", ""))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (exclusions.isEmpty()) {
            return foods;
        }
        return foods.stream()
                .filter(food -> exclusions.stream().noneMatch(exclusion -> foodSearchText(food).contains(exclusion)))
                .toList();
    }

    private PriceOrder resolvePriceOrder(String normalizedMessage, AiChatDecision aiDecision) {
        PriceOrder detected = detectPriceOrder(normalizedMessage);
        if (detected != PriceOrder.NONE) {
            return detected;
        }
        if (aiDecision != null && StringUtils.hasText(aiDecision.priceOrder())) {
            try {
                return PriceOrder.valueOf(aiDecision.priceOrder().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Dùng NONE nếu AI trả giá trị ngoài danh sách cho phép.
            }
        }
        return PriceOrder.NONE;
    }

    private PriceOrder detectPriceOrder(String normalizedMessage) {
        if (containsAny(normalizedMessage,
                "dat nhat", "mac nhat", "gia cao nhat", "cao nhat")) {
            return PriceOrder.HIGHEST;
        }
        if (containsAny(normalizedMessage,
                "re nhat", "gia thap nhat", "thap nhat")) {
            return PriceOrder.LOWEST;
        }
        return PriceOrder.NONE;
    }

    private int resolveResultLimit(String normalizedMessage,
                                   AiChatDecision aiDecision,
                                   PriceOrder priceOrder) {
        OptionalInt requestedCount = extractRequestedFoodCount(normalizedMessage);
        if (requestedCount.isPresent()) {
            return clampResultLimit(requestedCount.getAsInt());
        }
        if (detectPriceOrder(normalizedMessage) != PriceOrder.NONE) {
            return 1;
        }
        if (aiDecision != null && aiDecision.resultLimit() > 0) {
            return clampResultLimit(aiDecision.resultLimit());
        }
        return priceOrder == PriceOrder.NONE ? MAX_FOOD_RESULTS : 1;
    }

    private OptionalInt extractRequestedFoodCount(String normalizedMessage) {
        Matcher numericMatcher = NUMERIC_FOOD_COUNT.matcher(normalizedMessage);
        if (numericMatcher.find()) {
            try {
                return OptionalInt.of(Integer.parseInt(numericMatcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Thử nhận diện số viết bằng chữ bên dưới.
            }
        }

        for (Map.Entry<String, Integer> entry : VIETNAMESE_NUMBER_WORDS.entrySet()) {
            if (containsAny(normalizedMessage, entry.getKey() + " mon")) {
                return OptionalInt.of(entry.getValue());
            }
        }
        return OptionalInt.empty();
    }

    private int clampResultLimit(int requestedLimit) {
        return Math.max(1, Math.min(MAX_RANKED_FOOD_RESULTS, requestedLimit));
    }

    private List<Food> selectRankedFoods(List<Food> foods, PriceOrder priceOrder, int limit) {
        Comparator<Food> comparator = Comparator
                .comparing(Food::getGia, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(Food::getTenMonAn, String.CASE_INSENSITIVE_ORDER);
        if (priceOrder == PriceOrder.HIGHEST) {
            comparator = Comparator
                    .comparing(Food::getGia, Comparator.nullsLast(BigDecimal::compareTo))
                    .reversed()
                    .thenComparing(Food::getTenMonAn, String.CASE_INSENSITIVE_ORDER);
        }
        return foods.stream()
                .filter(food -> food.getGia() != null)
                .sorted(comparator)
                .limit(clampResultLimit(limit))
                .toList();
    }

    private String rankedFoodMessage(List<Food> selected, PriceOrder priceOrder) {
        if (selected.size() == 1) {
            Food food = selected.getFirst();
            String rankLabel = priceOrder == PriceOrder.HIGHEST ? "cao nhất" : "thấp nhất";
            return "Món có giá " + rankLabel + " hiện đang bán là " + food.getTenMonAn()
                    + ", giá " + formatMoney(food.getGia()) + ".";
        }
        String direction = priceOrder == PriceOrder.HIGHEST
                ? "cao nhất, được sắp xếp từ cao xuống thấp"
                : "thấp nhất, được sắp xếp từ thấp lên cao";
        return "Tôi tìm thấy " + selected.size() + " món có giá " + direction + ".";
    }

    private List<Food> selectWithinTotalBudget(List<Food> foods, BigDecimal budget, int limit) {
        if (foods.isEmpty() || budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        List<Food> ordered = new ArrayList<>();
        ordered.addAll(selectDiverseFoods(foods, Math.min(foods.size(), Math.max(limit * 3, limit))));
        for (Food food : foods) {
            if (!ordered.contains(food)) {
                ordered.add(food);
            }
        }

        List<Food> selected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Food food : ordered) {
            if (selected.size() >= limit || food.getGia() == null) {
                continue;
            }
            BigDecimal nextTotal = total.add(food.getGia());
            if (nextTotal.compareTo(budget) <= 0) {
                selected.add(food);
                total = nextTotal;
            }
        }
        return selected;
    }

    private String aiSafetyDisclaimer(AiChatDecision aiDecision, String normalizedMessage) {
        if (aiDecision != null && aiDecision.safetyConcern()) {
            return "Thông tin nguyên liệu chỉ mang tính tham khảo; nguy cơ dị ứng hoặc nhiễm chéo "
                    + "cần được xác nhận trực tiếp với nhân viên nhà hàng.";
        }
        if (aiDecision != null && !aiDecision.exclusions().isEmpty()) {
            return "Mô tả món chỉ mang tính tham khảo. Vui lòng xác nhận nguyên liệu và khẩu vị trực tiếp với nhân viên.";
        }
        return safetyDisclaimer(normalizedMessage);
    }

    private ChatSession resolveSession(String requestedSessionId, String qrToken) {
        ChatSession session = null;
        if (StringUtils.hasText(requestedSessionId)) {
            session = chatSessionRepository.findBySessionToken(requestedSessionId.trim()).orElse(null);
        }
        if (session == null) {
            session = new ChatSession();
            session.setSessionToken(UUID.randomUUID().toString());
        }
        if (StringUtils.hasText(qrToken)) {
            session.setQrToken(qrToken.trim());
        }
        session.setLastActivityAt(LocalDateTime.now());
        return chatSessionRepository.save(session);
    }

    private void saveMessage(ChatSession session,
                             String role,
                             String content,
                             String intent,
                             String metadataJson) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setMetadataJson(metadataJson);
        chatMessageRepository.save(message);
    }

    private String serializeMetadata(ChatbotResponse response, AiChatDecision aiDecision) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("foodCount", response.foods().size());
            metadata.put("promotionCount", response.promotions().size());
            metadata.put("hasOrder", response.order() != null);
            metadata.put("actionCount", response.actions().size());
            metadata.put("aiUsed", aiDecision != null);
            if (aiDecision != null) {
                metadata.put("aiModel", chatbotAiService.modelName());
                metadata.put("aiConfidence", aiDecision.confidence());
                metadata.put("guestCount", aiDecision.guestCount());
                metadata.put("budget", aiDecision.budget());
                metadata.put("budgetScope", aiDecision.budgetScope());
                metadata.put("priceOrder", aiDecision.priceOrder());
                metadata.put("resultLimit", aiDecision.resultLimit());
                metadata.put("suggestedAction", aiDecision.suggestedAction());
            }
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Intent detectIntent(String normalized) {
        if (containsAny(normalized,
                "di ung", "dị ung", "dau phong", "lac", "gluten", "nhiem cheo", "khong an duoc")) {
            return Intent.ALLERGY_SAFETY;
        }
        if (containsAny(normalized,
                "trang thai don", "don cua toi", "don hang cua toi", "mon dang nau", "theo doi don")) {
            return Intent.ORDER_STATUS;
        }
        if (containsAny(normalized, "goi nhan vien", "goi phuc vu", "can nhan vien", "can phuc vu")) {
            return Intent.CALL_WAITER;
        }
        if (containsAny(normalized, "thanh toan", "tinh tien", "goi tinh tien", "hoa don")) {
            return Intent.PAYMENT_SUPPORT;
        }
        if (containsAny(normalized, "mo cua", "dong cua", "gio hoat dong", "gio phuc vu", "may gio")) {
            return Intent.OPENING_HOURS;
        }
        if (containsAny(normalized, "dia chi", "o dau", "so dien thoai", "lien he", "email", "hotline")) {
            return Intent.CONTACT_INFO;
        }
        if (containsAny(normalized, "dat ban", "dat cho", "giu cho", "reservation")) {
            return Intent.RESERVATION_SUPPORT;
        }
        if (containsAny(normalized, "khuyen mai", "uu dai", "giam gia", "voucher", "ma giam")) {
            return Intent.PROMOTION_SEARCH;
        }
        if (containsAny(normalized, "goi y", "tu van", "nen an", "combo", "ngan sach")) {
            return Intent.FOOD_RECOMMENDATION;
        }
        if (containsAny(normalized,
                "mon", "thuc don", "do uong", "nuoc", "hai san", "mon chay", "trang mieng", "khai vi",
                "bo", "ga", "ca", "tom", "muc", "lau", "com", "mi", "salad", "banh")) {
            return Intent.FOOD_SEARCH;
        }
        if (containsAny(normalized, "xin chao", "chao", "hello", "hi", "bat dau")) {
            return Intent.GREETING;
        }
        return Intent.UNKNOWN;
    }

    private List<Food> filterFoods(List<Food> foods, String normalizedMessage, BigDecimal maxPrice) {
        boolean avoidSpicy = containsAny(normalizedMessage, "khong cay", "it cay");
        List<String> meaningfulTokens = Arrays.stream(normalizedMessage.split("\\s+"))
                .map(token -> token.replaceAll("[^a-z0-9]", ""))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .filter(token -> !token.matches("\\d+[a-z]*"))
                .filter(token -> !avoidSpicy || !"cay".equals(token))
                .toList();

        List<String> categoryPhrases = detectCategoryPhrases(normalizedMessage);

        return foods.stream()
                .filter(food -> Boolean.TRUE.equals(food.getTrangThai()))
                .filter(food -> maxPrice == null || food.getGia().compareTo(maxPrice) <= 0)
                .filter(food -> categoryPhrases.isEmpty()
                        || categoryPhrases.stream().anyMatch(phrase -> foodSearchText(food).contains(phrase)))
                .filter(food -> meaningfulTokens.isEmpty()
                        || meaningfulTokens.stream().anyMatch(token -> foodSearchText(food).contains(token)))
                .filter(food -> !avoidSpicy || !foodSearchText(food).contains("cay"))
                .sorted(Comparator.comparing(Food::getGia)
                        .thenComparing(Food::getTenMonAn, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<String> detectCategoryPhrases(String normalizedMessage) {
        List<String> phrases = new ArrayList<>();
        if (containsAny(normalizedMessage, "do uong", "nuoc uong", "tra", "ca phe")) {
            phrases.addAll(List.of("do uong", "nuoc", "tra", "ca phe"));
        }
        if (containsAny(normalizedMessage, "trang mieng", "banh", "kem")) {
            phrases.addAll(List.of("trang mieng", "banh", "kem"));
        }
        if (containsAny(normalizedMessage, "khai vi", "salad")) {
            phrases.addAll(List.of("khai vi", "salad"));
        }
        if (containsAny(normalizedMessage, "hai san", "tom", "muc", "ca")) {
            phrases.addAll(List.of("hai san", "tom", "muc", "ca"));
        }
        if (containsAny(normalizedMessage, "mon chay", "chay")) {
            phrases.add("chay");
        }
        return phrases.stream().distinct().toList();
    }

    private List<Food> selectDiverseFoods(List<Food> foods, int limit) {
        if (foods.size() <= limit) {
            return foods;
        }
        LinkedHashMap<String, Food> byCategory = new LinkedHashMap<>();
        for (Food food : foods) {
            String category = food.getDanhMuc() == null ? "" : food.getDanhMuc().getTenDanhMuc();
            byCategory.putIfAbsent(category, food);
            if (byCategory.size() >= limit) {
                return new ArrayList<>(byCategory.values());
            }
        }
        List<Food> result = new ArrayList<>(byCategory.values());
        for (Food food : foods) {
            if (result.size() >= limit) {
                break;
            }
            if (!result.contains(food)) {
                result.add(food);
            }
        }
        return result;
    }

    private Optional<BigDecimal> extractBudget(String normalizedMessage) {
        Matcher matcher = NUMBER_WITH_UNIT.matcher(normalizedMessage);
        BigDecimal best = null;
        while (matcher.find()) {
            String raw = matcher.group(1);
            String unit = matcher.group(2);
            BigDecimal value = parseNumber(raw, unit);
            if (value == null || value.compareTo(BigDecimal.valueOf(1_000)) < 0) {
                continue;
            }
            if (best == null || value.compareTo(best) > 0) {
                best = value;
            }
        }
        return Optional.ofNullable(best);
    }

    private BigDecimal parseNumber(String raw, String unit) {
        try {
            String normalizedUnit = unit == null ? "" : unit;
            if (Set.of("trieu", "m").contains(normalizedUnit)) {
                String decimal = raw.replace(',', '.');
                return new BigDecimal(decimal).multiply(BigDecimal.valueOf(1_000_000));
            }
            if (Set.of("nghin", "ngan", "k").contains(normalizedUnit)) {
                String decimal = raw.replace(',', '.');
                return new BigDecimal(decimal).multiply(BigDecimal.valueOf(1_000));
            }
            String digits = raw.replace(".", "").replace(",", "");
            return new BigDecimal(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String foodSearchText(Food food) {
        String category = food.getDanhMuc() == null ? "" : food.getDanhMuc().getTenDanhMuc();
        return normalize(String.join(" ",
                safe(food.getTenMonAn()),
                safe(food.getMoTa()),
                safe(category)
        ));
    }

    private ChatbotFoodResponse toFoodResponse(Food food) {
        return new ChatbotFoodResponse(
                food.getMaMonAn(),
                food.getTenMonAn(),
                food.getGia(),
                food.getMoTa(),
                food.getHinhAnh(),
                food.getDanhMuc() == null ? null : food.getDanhMuc().getTenDanhMuc(),
                Boolean.TRUE.equals(food.getTrangThai())
        );
    }

    private ChatbotPromotionResponse toPromotionResponse(Promotion promotion) {
        return new ChatbotPromotionResponse(
                promotion.getMaKhuyenMai(),
                promotion.getMaCode(),
                promotion.getTenKhuyenMai(),
                promotion.getMoTa(),
                promotion.getLoaiGiam(),
                promotion.getGiaTriGiam(),
                promotion.getGiaTriDonToiThieu(),
                promotion.getGiamToiDa(),
                promotion.getNgayBatDau(),
                promotion.getNgayKetThuc()
        );
    }

    private ChatbotOrderResponse toOrderResponse(Order order) {
        int itemCount = order.getChiTietDonHang() == null ? 0 : order.getChiTietDonHang().stream()
                .filter(item -> !"DA_HUY".equalsIgnoreCase(safe(item.getTrangThaiMon())))
                .map(OrderItem::getSoLuong)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        String status = safe(order.getTrangThai()).toUpperCase(Locale.ROOT);
        return new ChatbotOrderResponse(
                order.getMaDonHang(),
                status,
                ORDER_STATUS_LABELS.getOrDefault(status, status.replace('_', ' ')),
                itemCount,
                defaultMoney(order.getTamTinh()),
                defaultMoney(order.getTienGiam()),
                defaultMoney(order.getTongTien()),
                order.getThoiGianCapNhat()
        );
    }

    private List<ChatbotActionResponse> contactActions() {
        List<ChatbotActionResponse> actions = new ArrayList<>();
        if (isConfigured(restaurantInfo.getPhone())) {
            String phone = restaurantInfo.getPhone().replaceAll("[^+\\d]", "");
            actions.add(ChatbotActionResponse.link("Gọi nhà hàng", "CALL_RESTAURANT", "tel:" + phone));
        }
        actions.add(ChatbotActionResponse.link("Đặt bàn", "OPEN_RESERVATION", restaurantInfo.getReservationUrl()));
        return actions;
    }

    private String safetyDisclaimer(String normalizedMessage) {
        if (containsAny(normalizedMessage, "khong cay", "it cay", "chay", "khong an", "nguyen lieu")) {
            return "Mô tả món chỉ mang tính tham khảo. Vui lòng xác nhận nguyên liệu và khẩu vị trực tiếp với nhân viên.";
        }
        return null;
    }

    private boolean isConfigured(String value) {
        return StringUtils.hasText(value)
                && !"chưa cập nhật".equalsIgnoreCase(value.trim())
                && !"chua cap nhat".equalsIgnoreCase(normalize(value));
    }

    private boolean containsAny(String text, String... candidates) {
        String paddedText = " " + text + " ";
        for (String candidate : candidates) {
            String normalizedCandidate = normalize(candidate);
            if (normalizedCandidate.contains(" ")) {
                if (text.contains(normalizedCandidate)) {
                    return true;
                }
            } else if (paddedText.contains(" " + normalizedCandidate + " ")) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.,]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private String formatMoney(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return formatter.format(value.setScale(0, RoundingMode.HALF_UP)) + "đ";
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private enum PriceOrder {
        NONE,
        HIGHEST,
        LOWEST
    }

    private enum BudgetScope {
        TOTAL,
        PER_ITEM,
        UNKNOWN
    }

    private enum Intent {
        GREETING,
        OPENING_HOURS,
        CONTACT_INFO,
        FOOD_SEARCH,
        FOOD_RECOMMENDATION,
        PROMOTION_SEARCH,
        RESERVATION_SUPPORT,
        ORDER_STATUS,
        CALL_WAITER,
        PAYMENT_SUPPORT,
        ALLERGY_SAFETY,
        UNKNOWN
    }
}
