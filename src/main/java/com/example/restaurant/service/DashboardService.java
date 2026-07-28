package com.example.restaurant.service;

import com.example.restaurant.dto.DashboardResponse;
import com.example.restaurant.dto.RevenueByDayResponse;
import com.example.restaurant.dto.dashboard.ChartPointResponse;
import com.example.restaurant.dto.dashboard.OrderStatusChartResponse;
import com.example.restaurant.dto.dashboard.RecentActivityResponse;
import com.example.restaurant.dto.dashboard.RecentOrderResponse;
import com.example.restaurant.dto.dashboard.TopFoodResponse;
import com.example.restaurant.repository.DiningTableRepository;
import com.example.restaurant.repository.FoodRepository;
import com.example.restaurant.repository.InvoiceRepository;
import com.example.restaurant.repository.OrderItemRepository;
import com.example.restaurant.repository.OrderRepository;
import com.example.restaurant.repository.SystemActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {
    private static final String PAID_STATUS = "DA_THANH_TOAN";

    private final DiningTableRepository diningTableRepository;
    private final FoodRepository foodRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final SystemActivityRepository systemActivityRepository;

    public DashboardService(DiningTableRepository diningTableRepository,
                            FoodRepository foodRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            InvoiceRepository invoiceRepository,
                            SystemActivityRepository systemActivityRepository) {
        this.diningTableRepository = diningTableRepository;
        this.foodRepository = foodRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.invoiceRepository = invoiceRepository;
        this.systemActivityRepository = systemActivityRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);

        BigDecimal todayRevenue = invoiceRepository.totalRevenue(PAID_STATUS, start, end);

        return new DashboardResponse(
                diningTableRepository.count(),
                diningTableRepository.countByTrangThai("TRONG"),
                diningTableRepository.countByTrangThai("DANG_SU_DUNG"),
                foodRepository.countByTrangThaiTrue(),
                orderRepository.countByThoiGianDatBetween(start, end),
                orderRepository.countByTrangThaiAndThoiGianDatBetween("CHO_XAC_NHAN", start, end),
                orderRepository.countOrderHasItemStatus("DANG_CHE_BIEN"),
                invoiceRepository.countPaidInvoices(PAID_STATUS, start, end),
                todayRevenue,
                revenueLast7Days()
        );
    }

    @Transactional(readOnly = true)
    public List<RevenueByDayResponse> revenueLast7Days() {
        List<RevenueByDayResponse> result = new ArrayList<>();
        LocalDate startDay = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            LocalDate day = startDay.plusDays(i);
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.plusDays(1).atStartOfDay().minusNanos(1);
            BigDecimal revenue = invoiceRepository.totalRevenue(PAID_STATUS, from, to);
            long invoiceCount = invoiceRepository.countPaidInvoices(PAID_STATUS, from, to);
            result.add(new RevenueByDayResponse(day, revenue, invoiceCount));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChartPointResponse> revenueChart(LocalDate from, LocalDate to) {
        List<ChartPointResponse> result = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            LocalDateTime start = current.atStartOfDay();
            LocalDateTime end = current.plusDays(1).atStartOfDay().minusNanos(1);
            BigDecimal revenue = invoiceRepository.totalRevenue(PAID_STATUS, start, end);
            long invoiceCount = invoiceRepository.countPaidInvoices(PAID_STATUS, start, end);
            long orderCount = orderRepository.countByThoiGianDatBetween(start, end);
            result.add(new ChartPointResponse(current, revenue, invoiceCount, orderCount));
            current = current.plusDays(1);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OrderStatusChartResponse> orderStatusChart() {
        return orderRepository.countByTrangThaiGroup().stream()
                .map(row -> new OrderStatusChartResponse(
                        (String) row[0],
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopFoodResponse> topFoods(LocalDate from, LocalDate to, int limit) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay().minusNanos(1);
        return orderItemRepository.findTopFoods(start, end, PAID_STATUS).stream()
                .limit(normalizeLimit(limit, 10, 50))
                .map(row -> new TopFoodResponse(
                        (Integer) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue(),
                        row[4] == null ? BigDecimal.ZERO : (BigDecimal) row[4]
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecentOrderResponse> recentOrders(int limit) {
        int safeLimit = normalizeLimit(limit, 5, 20);
        return orderRepository.findAllByOrderByThoiGianDatDesc(PageRequest.of(0, safeLimit)).stream()
                .map(order -> new RecentOrderResponse(
                        order.getMaDonHang(),
                        order.getBanAn() == null ? null : order.getBanAn().getTenBan(),
                        order.getTongTien(),
                        order.getTrangThai(),
                        order.getThoiGianDat()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecentActivityResponse> recentActivities(int limit) {
        int safeLimit = normalizeLimit(limit, 5, 20);
        return systemActivityRepository.findAllByOrderByThoiGianDesc(PageRequest.of(0, safeLimit)).stream()
                .map(activity -> new RecentActivityResponse(
                        activity.getMaHoatDong(),
                        activity.getLoaiHoatDong(),
                        activity.getNoiDung(),
                        activity.getDoiTuongId(),
                        activity.getNguoiThucHien(),
                        activity.getThoiGian()
                ))
                .toList();
    }

    private int normalizeLimit(int limit, int defaultLimit, int maxLimit) {
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }
}
