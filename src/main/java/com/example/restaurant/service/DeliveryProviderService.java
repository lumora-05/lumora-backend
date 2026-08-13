package com.example.restaurant.service;

import com.example.restaurant.dto.DeliveryProviderAssignment;
import com.example.restaurant.entity.Order;

/**
 * Cổng tích hợp đơn vị vận chuyển.
 * Khi triển khai thật chỉ cần thay implementation mô phỏng bằng adapter gọi API đối tác.
 */
public interface DeliveryProviderService {
    DeliveryProviderAssignment createDelivery(Order order);
}
