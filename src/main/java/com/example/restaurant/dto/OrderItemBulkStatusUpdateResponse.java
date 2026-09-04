package com.example.restaurant.dto;

import java.util.List;

/** Phản hồi gọn cho thao tác cập nhật trạng thái nhiều món của bếp. */
public record OrderItemBulkStatusUpdateResponse(
        int updatedCount,
        List<Integer> itemIds,
        String trangThaiMon
) {
}
