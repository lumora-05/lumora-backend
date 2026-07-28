package com.example.restaurant.service;

import com.example.restaurant.entity.Food;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatbotPriceRankingTest {
    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotService(
                null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void detectsHighestAndLowestPriceQueriesWithoutAi() throws Exception {
        assertEquals("HIGHEST", detectPriceOrder("Món đắt nhất là món nào?"));
        assertEquals("LOWEST", detectPriceOrder("Món rẻ nhất là món nào?"));
        assertEquals("HIGHEST", detectPriceOrder("Món có giá cao nhất"));
        assertEquals("LOWEST", detectPriceOrder("Món có giá thấp nhất"));
    }

    @Test
    void extractsRequestedResultCountFromDigitsAndVietnameseWords() throws Exception {
        assertEquals(3, requestedCount("Ba món đắt nhất"));
        assertEquals(5, requestedCount("Top 5 món có giá cao nhất"));
    }

    @Test
    void removesRankingWordsBeforeSearchingFoodNames() throws Exception {
        assertEquals("", sanitize("Ba món đắt nhất"));
        assertEquals("hai san", sanitize("Ba món hải sản đắt nhất"));
        assertEquals("do uong", sanitize("Top 3 món đồ uống rẻ nhất"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sortsFoodsInRequestedPriceDirection() throws Exception {
        Food cheap = food(1, "Món A", 100_000);
        Food medium = food(2, "Món B", 200_000);
        Food expensive = food(3, "Món C", 300_000);

        Class<?> priceOrderClass = Class.forName(
                "com.example.restaurant.service.ChatbotService$PriceOrder"
        );
        Object highest = Enum.valueOf((Class) priceOrderClass, "HIGHEST");
        Object lowest = Enum.valueOf((Class) priceOrderClass, "LOWEST");
        Method select = ChatbotService.class.getDeclaredMethod(
                "selectRankedFoods", List.class, priceOrderClass, int.class
        );
        select.setAccessible(true);

        List<Food> topTwo = (List<Food>) select.invoke(
                chatbotService, List.of(cheap, expensive, medium), highest, 2
        );
        List<Food> cheapest = (List<Food>) select.invoke(
                chatbotService, List.of(cheap, expensive, medium), lowest, 1
        );

        assertEquals(List.of("Món C", "Món B"),
                topTwo.stream().map(Food::getTenMonAn).toList());
        assertEquals("Món A", cheapest.getFirst().getTenMonAn());
    }

    private String detectPriceOrder(String query) throws Exception {
        Method normalize = ChatbotService.class.getDeclaredMethod("normalize", String.class);
        normalize.setAccessible(true);
        Method detect = ChatbotService.class.getDeclaredMethod("detectPriceOrder", String.class);
        detect.setAccessible(true);
        String normalized = (String) normalize.invoke(chatbotService, query);
        return detect.invoke(chatbotService, normalized).toString();
    }

    private int requestedCount(String query) throws Exception {
        Method normalize = ChatbotService.class.getDeclaredMethod("normalize", String.class);
        normalize.setAccessible(true);
        Method extract = ChatbotService.class.getDeclaredMethod(
                "extractRequestedFoodCount", String.class
        );
        extract.setAccessible(true);
        String normalized = (String) normalize.invoke(chatbotService, query);
        OptionalInt result = (OptionalInt) extract.invoke(chatbotService, normalized);
        assertTrue(result.isPresent());
        return result.getAsInt();
    }

    private String sanitize(String query) throws Exception {
        Method normalize = ChatbotService.class.getDeclaredMethod("normalize", String.class);
        normalize.setAccessible(true);
        Method sanitize = ChatbotService.class.getDeclaredMethod("sanitizeFoodQuery", String.class);
        sanitize.setAccessible(true);
        String normalized = (String) normalize.invoke(chatbotService, query);
        return (String) sanitize.invoke(chatbotService, normalized);
    }

    private Food food(int id, String name, long price) {
        Food food = new Food();
        food.setMaMonAn(id);
        food.setTenMonAn(name);
        food.setGia(BigDecimal.valueOf(price));
        food.setTrangThai(true);
        return food;
    }
}
