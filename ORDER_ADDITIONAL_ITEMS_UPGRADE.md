# Nâng cấp luồng gọi thêm món

## Luồng sau nâng cấp

- Lần gọi đầu tiên của bàn: tạo một `don_hang` mới.
- Các lần gọi tiếp theo: tìm đơn đang mở của bàn và thêm `chi_tiet_don_hang` vào cùng đơn.
- Backend trả lại cùng `maDonHang` cho mọi lần gọi thêm.
- Mỗi chi tiết món có:
  - `lanGoi`: 1, 2, 3...
  - `thoiGianThem`: thời điểm món được gửi xuống bếp.
- Tổng tiền của đơn được cộng lại sau mỗi lượt gọi.
- Bếp nhận sự kiện `NEW_KITCHEN_ITEMS` và chỉ cần tải các món có trạng thái `CHO_BEP`.
- Khi toàn bộ món hoàn thành, đơn tự chuyển sang `SAN_SANG_PHUC_VU`.
- Chỉ đơn `CHO_THANH_TOAN` hoặc `SAN_SANG_THANH_TOAN` mới được tạo hóa đơn.
- Thanh toán chỉ đưa bàn về `TRONG` nếu không còn đơn mở khác từ dữ liệu cũ.

## API

API gửi món không đổi:

```http
POST /api/customer/orders
```

Nếu bàn chưa có đơn mở, API tạo đơn. Nếu đã có, API thêm món vào đơn đó.

API hỗ trợ khôi phục đơn sau khi tải lại trang:

```http
GET /api/customer/tables/{tableId}/orders/current
GET /api/customer/tables/{tableId}/orders
```

Theo dõi chi tiết:

```http
GET /api/customer/orders/{orderId}
```

## Database

Với database đã có dữ liệu, chạy:

```text
database/order_additional_items_upgrade.sql
```

`spring.jpa.hibernate.ddl-auto=update` cũng có thể tự thêm cột, nhưng file SQL sẽ
backfill dữ liệu cũ và tạo index đầy đủ hơn.

## WebSocket mới

- `/topic/kitchen` — `NEW_KITCHEN_ITEMS`
- `/topic/orders` — `ORDER_ITEMS_ADDED`
- `/topic/customer/orders/{orderId}` — cập nhật riêng cho một đơn
- `/topic/customer/tables/{tableId}` — cập nhật riêng cho một bàn
