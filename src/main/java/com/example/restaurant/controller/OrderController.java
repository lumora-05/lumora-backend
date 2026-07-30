package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.OrderCreateRequest;
import com.example.restaurant.dto.OrderItemCancellationDecisionRequest;
import com.example.restaurant.dto.OrderItemCancellationRequest;
import com.example.restaurant.dto.OrderItemCancellationResponse;
import com.example.restaurant.dto.OrderItemStatusUpdateRequest;
import com.example.restaurant.dto.OrderStatusUpdateRequest;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('WAITER')")
    public ResponseEntity<ApiResponse<Order>> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                                           Authentication authentication) {
        Order order = orderService.createOrderByStaff(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Đã gửi đơn hàng trực tiếp vào bếp", order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Order>> detail(@PathVariable Integer id, Authentication authentication) {
        Order order;
        if (isKitchenOnly(authentication)) {
            order = orderService.findByIdForKitchen(id);
        } else if (isWaiterOnly(authentication)) {
            order = orderService.findByIdForWaiter(id, authentication.getName());
        } else {
            order = orderService.findById(id);
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", order));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAITER','KITCHEN','CASHIER')")
    public ResponseEntity<ApiResponse<List<Order>>> allOrders(Authentication authentication) {
        List<Order> orders;
        if (isKitchenOnly(authentication)) {
            orders = orderService.findAllForKitchen();
        } else if (isWaiterOnly(authentication)) {
            orders = orderService.findAllForWaiter(authentication.getName());
        } else {
            orders = orderService.findAll();
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công", orders));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER','KITCHEN','CASHIER')")
    public ResponseEntity<ApiResponse<PageResponse<Order>>> pagedOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        PageResponse<Order> result = PageResponse.from(
                orderService.findPage(
                        page,
                        size,
                        keyword,
                        status,
                        from,
                        to,
                        isKitchenOnly(authentication),
                        isWaiterOnly(authentication) ? authentication.getName() : null
                )
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng phân trang thành công", result));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER','KITCHEN','CASHIER')")
    public ResponseEntity<ApiResponse<List<Order>>> byStatus(@PathVariable String status,
                                                              Authentication authentication) {
        List<Order> orders;
        if (isKitchenOnly(authentication)) {
            orders = orderService.findByStatusForKitchen(status);
        } else if (isWaiterOnly(authentication)) {
            orders = orderService.findByStatusForWaiter(status, authentication.getName());
        } else {
            orders = orderService.findByStatus(status);
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng theo trạng thái thành công", orders));
    }

    @GetMapping("/kitchen/items/{status}")
    @PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
    public ResponseEntity<ApiResponse<List<OrderItem>>> kitchenItems(@PathVariable String status) {
        List<OrderItem> orderItems = orderService.findKitchenItems(status);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách món cần chế biến thành công", orderItems));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(@PathVariable Integer id,
                                                                 @Valid @RequestBody OrderStatusUpdateRequest request,
                                                                 Authentication authentication) {
        Order order = orderService.updateOrderStatus(
                id,
                request,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đơn hàng thành công", order));
    }

    /**
     * Nhân viên phục vụ ghi nhận yêu cầu thanh toán khi khách gọi trực tiếp.
     * Endpoint nghiệp vụ riêng, không cho frontend truyền trạng thái tùy ý.
     */
    @PostMapping("/{id}/request-payment")
    @PreAuthorize("hasRole('WAITER')")
    public ResponseEntity<ApiResponse<Order>> requestPayment(@PathVariable Integer id,
                                                              Authentication authentication) {
        Order order = orderService.requestPaymentByWaiter(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Đã chuyển yêu cầu thanh toán đến thu ngân",
                order
        ));
    }


    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private boolean isKitchenOnly(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean kitchen = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_KITCHEN".equals(authority.getAuthority()));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return kitchen && !admin;
    }

    private boolean isWaiterOnly(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean waiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_WAITER".equals(authority.getAuthority()));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return waiter && !admin;
    }

    @PutMapping("/items/{itemId}/status")
    @PreAuthorize("hasRole('KITCHEN')")
    public ResponseEntity<ApiResponse<OrderItem>> updateItemStatus(@PathVariable Integer itemId,
                                                                    @Valid @RequestBody OrderItemStatusUpdateRequest request,
                                                                    Authentication authentication) {
        OrderItem orderItem = orderService.updateItemStatus(itemId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái món ăn thành công", orderItem));
    }

    /**
     * Nghiệp vụ riêng dành cho nhân viên phục vụ xác nhận món đã được mang ra bàn.
     * Không nhận trạng thái từ frontend để tránh phục vụ sửa trạng thái của bếp.
     */
    @PutMapping("/items/{itemId}/served")
    @PreAuthorize("hasRole('WAITER')")
    public ResponseEntity<ApiResponse<OrderItem>> markItemServed(@PathVariable Integer itemId,
                                                                  Authentication authentication) {
        OrderItem orderItem = orderService.markItemServed(itemId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Xác nhận món đã phục vụ thành công", orderItem));
    }

    /** Phục vụ/admin hủy món và bắt buộc chọn lý do. */
    @PostMapping("/items/{itemId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<OrderItem>> cancelItem(
            @PathVariable Integer itemId,
            @Valid @RequestBody OrderItemCancellationRequest request,
            Authentication authentication) {
        OrderItem item = orderService.cancelOrRequestItemCancellation(
                itemId,
                request,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        String message = "CHO_DUYET".equalsIgnoreCase(item.getTrangThaiHuy())
                ? "Đã gửi yêu cầu hủy món đến admin"
                : "Hủy món thành công";
        return ResponseEntity.ok(ApiResponse.success(message, item));
    }

    /** Danh sách yêu cầu hủy; phục vụ chỉ thấy bàn thuộc khu vực được phân công. */
    @GetMapping("/items/cancel-requests")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<List<OrderItemCancellationResponse>>> cancellationRequests(
            @RequestParam(defaultValue = "CHO_DUYET") String status,
            Authentication authentication) {
        List<OrderItemCancellationResponse> result = orderService.findItemCancellationRequests(
                status,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu hủy món thành công", result));
    }

    /** Phục vụ duyệt yêu cầu của khách khi món chưa nấu; admin duyệt được món đang nấu. */
    @PutMapping("/items/{itemId}/cancel-request/approve")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<OrderItem>> approveCancellation(
            @PathVariable Integer itemId,
            @Valid @RequestBody(required = false) OrderItemCancellationDecisionRequest request,
            Authentication authentication) {
        OrderItem item = orderService.approveItemCancellation(
                itemId,
                request,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt hủy món", item));
    }

    /** Từ chối yêu cầu và khôi phục trạng thái món trước khi yêu cầu hủy. */
    @PutMapping("/items/{itemId}/cancel-request/reject")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<OrderItem>> rejectCancellation(
            @PathVariable Integer itemId,
            @Valid @RequestBody(required = false) OrderItemCancellationDecisionRequest request,
            Authentication authentication) {
        OrderItem item = orderService.rejectItemCancellation(
                itemId,
                request,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu hủy món", item));
    }
}
