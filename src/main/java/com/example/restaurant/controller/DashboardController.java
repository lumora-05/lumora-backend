package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.DashboardResponse;
import com.example.restaurant.dto.RevenueByDayResponse;
import com.example.restaurant.dto.dashboard.ChartPointResponse;
import com.example.restaurant.dto.dashboard.OrderStatusChartResponse;
import com.example.restaurant.dto.dashboard.RecentActivityResponse;
import com.example.restaurant.dto.dashboard.RecentOrderResponse;
import com.example.restaurant.dto.dashboard.TopFoodResponse;
import com.example.restaurant.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu dashboard thành công", dashboardService.summary()));
    }

    @GetMapping("/revenue/last-7-days")
    public ResponseEntity<ApiResponse<List<RevenueByDayResponse>>> revenueLast7Days() {
        return ResponseEntity.ok(ApiResponse.success("Lấy doanh thu 7 ngày gần nhất thành công", dashboardService.revenueLast7Days()));
    }

    @GetMapping("/charts/revenue")
    public ResponseEntity<ApiResponse<List<ChartPointResponse>>> revenueChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu biểu đồ doanh thu thành công", dashboardService.revenueChart(start, end)));
    }

    @GetMapping("/charts/order-status")
    public ResponseEntity<ApiResponse<List<OrderStatusChartResponse>>> orderStatusChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy dữ liệu biểu đồ trạng thái đơn hàng thành công",
                dashboardService.orderStatusChart(start, end)
        ));
    }

    @GetMapping("/charts/top-foods")
    public ResponseEntity<ApiResponse<List<TopFoodResponse>>> topFoods(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu top món bán chạy thành công", dashboardService.topFoods(start, end, limit)));
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<ApiResponse<List<RecentOrderResponse>>> recentOrders(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Lấy đơn hàng mới nhất thành công", dashboardService.recentOrders(limit)));
    }

    @GetMapping("/recent-activities")
    public ResponseEntity<ApiResponse<List<RecentActivityResponse>>> recentActivities(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Lấy hoạt động gần đây thành công", dashboardService.recentActivities(limit)));
    }
}
