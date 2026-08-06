package com.example.restaurant.service;

import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.entity.Promotion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Nguồn tính tiền duy nhất của đơn hàng. Mọi nơi thêm/hủy món hoặc áp dụng
 * khuyến mãi đều gọi lại service này để tránh cộng trừ trực tiếp nhiều lần.
 */
@Service
public class OrderPricingService {

    public BigDecimal calculateSubtotal(Order order) {
        if (order == null || order.getChiTietDonHang() == null) {
            return money(BigDecimal.ZERO);
        }

        BigDecimal subtotal = order.getChiTietDonHang().stream()
                .filter(this::isChargeableItem)
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return money(subtotal);
    }

    public void recalculate(Order order) {
        BigDecimal subtotal = calculateSubtotal(order);
        BigDecimal discount = BigDecimal.ZERO;

        Promotion promotion = order.getKhuyenMai();
        if (promotion != null && isMinimumSatisfied(subtotal, promotion)) {
            discount = calculateDiscount(subtotal, promotion);
        }

        discount = discount.max(BigDecimal.ZERO).min(subtotal);
        boolean chargeDeliveryFee = order.getGiaoHang() != null
                && subtotal.signum() > 0
                && !"DA_HUY".equalsIgnoreCase(order.getTrangThai());
        BigDecimal deliveryFee = chargeDeliveryFee
                ? defaultMoney(order.getGiaoHang().getPhiGiaoHang())
                : BigDecimal.ZERO;
        order.setTamTinh(money(subtotal));
        order.setTienGiam(money(discount));
        order.setTongTien(money(subtotal.subtract(discount).add(deliveryFee)));
    }

    public BigDecimal calculateDiscount(BigDecimal subtotal, Promotion promotion) {
        if (promotion == null || subtotal == null || subtotal.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }

        BigDecimal discountValue = defaultMoney(promotion.getGiaTriGiam());
        BigDecimal discount;
        String type = normalizeType(promotion.getLoaiGiam());
        if ("PERCENT".equals(type)) {
            discount = subtotal.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = discountValue;
        }

        BigDecimal maximumDiscount = promotion.getGiamToiDa();
        if (maximumDiscount != null && maximumDiscount.signum() > 0) {
            discount = discount.min(maximumDiscount);
        }
        return money(discount.min(subtotal));
    }

    public boolean isMinimumSatisfied(BigDecimal subtotal, Promotion promotion) {
        BigDecimal minimum = promotion == null
                ? BigDecimal.ZERO
                : defaultMoney(promotion.getGiaTriDonToiThieu());
        return defaultMoney(subtotal).compareTo(minimum) >= 0;
    }

    public String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PERCENT", "PHAN_TRAM", "PERCENTAGE" -> "PERCENT";
            case "FIXED", "SO_TIEN", "TIEN", "AMOUNT" -> "FIXED";
            default -> normalized;
        };
    }

    private boolean isChargeableItem(OrderItem item) {
        return item != null && !"DA_HUY".equalsIgnoreCase(item.getTrangThaiMon());
    }

    private BigDecimal lineTotal(OrderItem item) {
        BigDecimal unitPrice = defaultMoney(item.getDonGia());
        int quantity = item.getSoLuong() == null ? 0 : Math.max(item.getSoLuong(), 0);
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP);
    }
}
