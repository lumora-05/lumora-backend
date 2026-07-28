# Thông báo nguyên liệu sắp hết cho Admin

## Quy tắc cảnh báo

- `HET_HANG`: tồn kho nhỏ hơn hoặc bằng `0`.
- `SAP_HET`: tồn kho lớn hơn `0` và nhỏ hơn hoặc bằng mức tồn tối thiểu.
- `CON_HANG`: tồn kho lớn hơn mức tồn tối thiểu.

Thông báo được tạo khi nguyên liệu chuyển sang `SAP_HET` hoặc `HET_HANG`.
Mỗi nguyên liệu chỉ có tối đa một thông báo chưa đọc cho cùng trạng thái để tránh lặp.
Khi tồn kho được bổ sung cao hơn mức tối thiểu, cảnh báo chưa đọc của nguyên liệu được tự động xử lý.

Khi ứng dụng khởi động, backend tự đồng bộ các nguyên liệu đang sắp hết/hết hàng để không bỏ sót dữ liệu cũ.

## REST API

Tất cả API yêu cầu JWT của `ADMIN`.

### Danh sách thông báo

```http
GET /api/admin/notifications?page=0&size=10&unread=true&type=NGUYEN_LIEU_SAP_HET&keyword=thịt
```

Tham số tùy chọn:

- `unread=true`: chỉ lấy thông báo chưa đọc.
- `unread=false`: chỉ lấy thông báo đã đọc.
- `type=NGUYEN_LIEU_SAP_HET` hoặc `NGUYEN_LIEU_HET_HANG`.
- `keyword`: tìm theo tiêu đề, nội dung hoặc tên nguyên liệu.

### Số lượng chưa đọc

```http
GET /api/admin/notifications/unread-count
```

Dữ liệu trả về:

```json
{
  "soLuongChuaDoc": 3
}
```

### Đánh dấu một thông báo đã đọc

```http
PATCH /api/admin/notifications/{id}/read
```

### Đánh dấu tất cả đã đọc

```http
PATCH /api/admin/notifications/read-all
```

### Đồng bộ thủ công dữ liệu tồn kho thấp

```http
POST /api/admin/notifications/sync-low-stock
```

Thông thường không cần gọi API này vì backend đã tự đồng bộ khi khởi động.

## WebSocket

Đăng ký STOMP topic:

```text
/topic/admin/notifications
```

Loại sự kiện:

- `INVENTORY_LOW_STOCK_ALERT`: có nguyên liệu sắp hết hoặc hết hàng.
- `INVENTORY_STOCK_RECOVERED`: tồn kho đã được bổ sung cao hơn mức tối thiểu.

Frontend nên nhận sự kiện rồi tải lại:

- `GET /api/admin/notifications/unread-count`
- `GET /api/admin/notifications?page=0&size=10`
