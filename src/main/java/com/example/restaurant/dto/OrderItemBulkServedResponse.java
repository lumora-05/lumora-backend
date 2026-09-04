package com.example.restaurant.dto;

import java.util.List;

/** Phản hồi gọn cho thao tác phục vụ nhiều món trong một lần. */
public record OrderItemBulkServedResponse(
        int updatedCount,
        List<Integer> itemIds
) {
}
