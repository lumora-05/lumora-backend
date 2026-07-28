# API yêu cầu phục vụ tại bàn

## Phạm vi nghiệp vụ

Luồng trạng thái:

```text
MOI -> DA_TIEP_NHAN -> HOAN_THANH
  \-----------------> DA_HUY
```

Loại yêu cầu hỗ trợ:

- `GOI_NHAN_VIEN`: Gọi nhân viên
- `THEM_NUOC`: Xin thêm nước
- `THEM_DUNG_CU`: Xin thêm dụng cụ
- `THEM_KHAN_GIAY`: Xin thêm khăn giấy
- `DON_BAN`: Dọn bàn
- `YEU_CAU_KHAC`: Yêu cầu khác, bắt buộc nhập `noiDung`

Quy tắc:

- Mỗi bàn chỉ có một yêu cầu đang mở cho cùng loại.
- Mỗi bàn có tối đa 3 yêu cầu đang mở.
- Phục vụ chỉ xem và xử lý yêu cầu thuộc khu vực được phân công.
- Khóa pessimistic bảo đảm hai phục vụ không thể cùng tiếp nhận một yêu cầu.
- Chỉ nhân viên đã tiếp nhận mới được hoàn thành yêu cầu.
- Khách chỉ tự hủy khi yêu cầu còn `MOI`; admin có thể hủy yêu cầu đang mở.
- Khi chuyển bàn, yêu cầu đang mở đi theo bàn mới.
- Khi phiên phục vụ kết thúc, yêu cầu đang mở tự chuyển `DA_HUY`.

## API khách hàng

### Tạo yêu cầu

```http
POST /api/customer/qr/{qrToken}/service-requests
Content-Type: application/json
```

```json
{
  "loaiYeuCau": "THEM_KHAN_GIAY",
  "noiDung": ""
}
```

### Xem 20 yêu cầu gần nhất

```http
GET /api/customer/qr/{qrToken}/service-requests
```

### Xem yêu cầu đang mở

```http
GET /api/customer/qr/{qrToken}/service-requests/active
```

### Khách hủy yêu cầu chưa được tiếp nhận

```http
PUT /api/customer/qr/{qrToken}/service-requests/{requestId}/cancel
Content-Type: application/json
```

```json
{
  "lyDo": "Khách không còn cần hỗ trợ"
}
```

Body có thể bỏ trống.

## API phục vụ và admin

### Danh sách yêu cầu

```http
GET /api/service-requests?status=ACTIVE
Authorization: Bearer <token>
```

`status` nhận: `ACTIVE`, `ALL`, `MOI`, `DA_TIEP_NHAN`, `HOAN_THANH`, `DA_HUY`.

### Phục vụ tiếp nhận

```http
PUT /api/service-requests/{requestId}/accept
Authorization: Bearer <WAITER_TOKEN>
```

### Phục vụ hoàn thành

```http
PUT /api/service-requests/{requestId}/complete
Authorization: Bearer <WAITER_TOKEN>
```

### Admin hủy

```http
PUT /api/service-requests/{requestId}/cancel
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "lyDo": "Yêu cầu không còn phù hợp"
}
```

## WebSocket

Các màn hình phục vụ/admin tải lại danh sách khi nhận sự kiện tại:

```text
/topic/service-requests
/topic/admin/service-requests
```

Trang khách của bàn nhận cập nhật tại:

```text
/topic/customer/tables/{tableId}/service-requests
/topic/customer/tables/{tableId}
```

Loại sự kiện:

```text
SERVICE_REQUEST_CREATED
SERVICE_REQUEST_ACCEPTED
SERVICE_REQUEST_COMPLETED
SERVICE_REQUEST_CANCELLED
SERVICE_REQUEST_TRANSFERRED
```
