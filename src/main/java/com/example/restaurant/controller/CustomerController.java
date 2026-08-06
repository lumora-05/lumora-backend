package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.CustomerTableResponse;
import com.example.restaurant.dto.OrderCreateRequest;
import com.example.restaurant.dto.OrderItemCancellationRequest;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.dto.PromotionCodeRequest;
import com.example.restaurant.entity.Food;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.service.CategoryService;
import com.example.restaurant.service.MenuService;
import com.example.restaurant.service.OrderService;
import com.example.restaurant.service.PromotionService;
import com.example.restaurant.service.TableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {
    private final TableService tableService;
    private final CategoryService categoryService;
    private final MenuService menuService;
    private final OrderService orderService;
    private final PromotionService promotionService;

    public CustomerController(TableService tableService,
                              CategoryService categoryService,
                              MenuService menuService,
                              OrderService orderService,
                              PromotionService promotionService) {
        this.tableService = tableService;
        this.categoryService = categoryService;
        this.menuService = menuService;
        this.orderService = orderService;
        this.promotionService = promotionService;
    }

    @GetMapping("/tables/{tableId}")
    public ResponseEntity<ApiResponse<CustomerTableResponse>> tableMenu(@PathVariable Integer tableId) {
        CustomerTableResponse response = new CustomerTableResponse(
                tableService.findCustomerAccessibleTable(tableId),
                categoryService.findActive(),
                menuService.findActiveFoods()
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bàn và thực đơn cho khách hàng thành công", response));
    }

    /** API mới: xác định bàn bằng QR token thay vì mã bàn có thể đoán. */
    @GetMapping("/qr/{qrToken}")
    public ResponseEntity<ApiResponse<CustomerTableResponse>> tableMenuByQrToken(@PathVariable String qrToken) {
        CustomerTableResponse response = new CustomerTableResponse(
                tableService.findCustomerAccessibleTableByToken(qrToken),
                categoryService.findActive(),
                menuService.findActiveFoods()
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bàn và thực đơn từ mã QR thành công", response));
    }

    /**
     * Thực đơn phân trang dành cho khách hàng.
     * API thông tin bàn cũ vẫn được giữ nguyên để tương thích với frontend hiện tại.
     */
    @GetMapping("/tables/{tableId}/menu")
    public ResponseEntity<ApiResponse<PageResponse<Food>>> pagedTableMenu(
            @PathVariable Integer tableId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId) {
        // Bảo đảm QR/bàn hợp lệ và đang cho phép khách truy cập trước khi trả thực đơn.
        tableService.findCustomerAccessibleTable(tableId);

        PageResponse<Food> result = PageResponse.from(
                menuService.findCustomerPage(page, size, keyword, categoryId)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thực đơn phân trang cho khách hàng thành công",
                result
        ));
    }

    @GetMapping("/qr/{qrToken}/menu")
    public ResponseEntity<ApiResponse<PageResponse<Food>>> pagedTableMenuByQrToken(
            @PathVariable String qrToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId) {
        tableService.findCustomerAccessibleTableByToken(qrToken);

        PageResponse<Food> result = PageResponse.from(
                menuService.findCustomerPage(page, size, keyword, categoryId)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thực đơn phân trang từ mã QR thành công",
                result
        ));
    }

    /**
     * Lần đầu tạo đơn mới; các lần tiếp theo của cùng bàn sẽ thêm món vào đơn đang mở.
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<Order>> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Order order = orderService.createCustomerOrder(request);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi đơn hàng trực tiếp vào bếp", order));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<Order>> orderTracking(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Theo dõi trạng thái đơn hàng thành công",
                orderService.findTableOrderForCustomer(orderId)
        ));
    }

    /**
     * Khách yêu cầu thanh toán sau khi đơn đã được phục vụ.
     * Endpoint riêng không cho phép khách tự truyền trạng thái đơn hàng.
     */
    @PostMapping("/orders/{orderId}/request-payment")
    public ResponseEntity<ApiResponse<Order>> requestPayment(@PathVariable Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gửi yêu cầu thanh toán đến thu ngân",
                orderService.requestPaymentByCustomer(orderId)
        ));
    }

    /**
     * Khách gửi yêu cầu hủy món chưa bắt đầu chế biến từ đúng mã QR của bàn.
     * Yêu cầu cần phục vụ hoặc admin duyệt, khách không được tự xóa món khỏi đơn.
     */
    @PostMapping("/qr/{qrToken}/orders/{orderId}/items/{itemId}/cancel-request")
    public ResponseEntity<ApiResponse<OrderItem>> requestItemCancellation(
            @PathVariable String qrToken,
            @PathVariable Integer orderId,
            @PathVariable Integer itemId,
            @Valid @RequestBody OrderItemCancellationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gửi yêu cầu hủy món đến nhân viên phục vụ",
                orderService.requestItemCancellationByCustomer(qrToken, orderId, itemId, request)
        ));
    }

    /** Khách áp dụng một mã khuyến mãi cho đơn đã được phục vụ. */
    @PostMapping("/orders/{orderId}/promotion")
    public ResponseEntity<ApiResponse<Order>> applyPromotion(
            @PathVariable Integer orderId,
            @Valid @RequestBody PromotionCodeRequest request) {
        orderService.findTableOrderForCustomer(orderId);
        return ResponseEntity.ok(ApiResponse.success(
                "Áp dụng khuyến mãi thành công",
                promotionService.applyToOrder(orderId, request.maCode())
        ));
    }

    /** Khách gỡ mã khuyến mãi trước khi thanh toán. */
    @DeleteMapping("/orders/{orderId}/promotion")
    public ResponseEntity<ApiResponse<Order>> removePromotion(@PathVariable Integer orderId) {
        orderService.findTableOrderForCustomer(orderId);
        return ResponseEntity.ok(ApiResponse.success(
                "Gỡ khuyến mãi thành công",
                promotionService.removeFromOrder(orderId)
        ));
    }

    /** Đơn đang mở hiện tại của bàn, dùng khi khách tải lại trang hoặc mở lại trình duyệt. */
    @GetMapping("/tables/{tableId}/orders/current")
    public ResponseEntity<ApiResponse<Order>> currentOrder(@PathVariable Integer tableId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy đơn hàng đang phục vụ của bàn thành công",
                orderService.findCurrentOrderByTable(tableId)
        ));
    }

    @GetMapping("/qr/{qrToken}/orders/current")
    public ResponseEntity<ApiResponse<Order>> currentOrderByQrToken(@PathVariable String qrToken) {
        Integer tableId = tableService.findCustomerAccessibleTableByToken(qrToken).getMaBan();
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy đơn hàng đang phục vụ từ mã QR thành công",
                orderService.findCurrentOrderByTable(tableId)
        ));
    }

    /**
     * Danh sách đơn còn mở của bàn. Sau nâng cấp thông thường chỉ có tối đa một đơn;
     * danh sách vẫn được giữ để tương thích dữ liệu cũ có thể đã phát sinh đơn trùng.
     */
    @GetMapping("/tables/{tableId}/orders")
    public ResponseEntity<ApiResponse<List<Order>>> openOrders(@PathVariable Integer tableId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy đơn hàng đang phục vụ của bàn thành công",
                orderService.findOpenOrdersByTable(tableId)
        ));
    }

    @GetMapping("/qr/{qrToken}/orders")
    public ResponseEntity<ApiResponse<List<Order>>> openOrdersByQrToken(@PathVariable String qrToken) {
        Integer tableId = tableService.findCustomerAccessibleTableByToken(qrToken).getMaBan();
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách đơn đang phục vụ từ mã QR thành công",
                orderService.findOpenOrdersByTable(tableId)
        ));
    }
}
