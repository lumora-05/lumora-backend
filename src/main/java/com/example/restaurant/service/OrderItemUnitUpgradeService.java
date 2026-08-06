package com.example.restaurant.service;

import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Nâng cấp dữ liệu cũ: các món vẫn đang chờ bếp nhưng được lưu gộp số lượng
 * sẽ được tách thành từng suất có số lượng bằng 1.
 */
@Service
public class OrderItemUnitUpgradeService {
    private final OrderItemRepository orderItemRepository;

    public OrderItemUnitUpgradeService(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public void splitLegacyWaitingItems() {
        List<OrderItem> groupedItems = orderItemRepository
                .findByTrangThaiMonAndSoLuongGreaterThanAndTrangThaiHuyIsNullOrderByMaChiTietAsc(
                        "CHO_BEP",
                        1
                );
        if (groupedItems.isEmpty()) {
            return;
        }

        List<OrderItem> itemsToSave = new ArrayList<>();
        for (OrderItem groupedItem : groupedItems) {
            int quantity = groupedItem.getSoLuong() == null ? 0 : groupedItem.getSoLuong();
            if (quantity <= 1) {
                continue;
            }

            groupedItem.setSoLuong(1);
            itemsToSave.add(groupedItem);

            for (int unit = 1; unit < quantity; unit++) {
                OrderItem item = new OrderItem();
                item.setDonHang(groupedItem.getDonHang());
                item.setMonAn(groupedItem.getMonAn());
                item.setSoLuong(1);
                item.setDonGia(groupedItem.getDonGia());
                item.setGhiChu(groupedItem.getGhiChu());
                item.setTrangThaiMon(groupedItem.getTrangThaiMon());
                item.setLanGoi(groupedItem.getLanGoi());
                item.setThoiGianThem(groupedItem.getThoiGianThem());
                itemsToSave.add(item);
            }
        }

        if (!itemsToSave.isEmpty()) {
            orderItemRepository.saveAllAndFlush(itemsToSave);
        }
    }
}
