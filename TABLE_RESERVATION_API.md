# API đặt bàn

## Luồng trạng thái

```text
CHO_XAC_NHAN
  -> DA_XAC_NHAN
  -> KHACH_DA_DEN
  -> DA_XEP_BAN
  -> HOAN_THANH
```

Nhánh kết thúc khác:

```text
CHO_XAC_NHAN -> TU_CHOI
CHO_XAC_NHAN / DA_XAC_NHAN -> DA_HUY
DA_XAC_NHAN -> KHONG_DEN
```

Quy tắc chính:

- Khách chỉ chọn khu vực mong muốn, nhân viên chọn bàn dự kiến khi xác nhận.
- Mỗi lần xác nhận/xếp bàn đều khóa bản ghi bàn và kiểm tra trùng khung giờ.
- Bàn dự kiến không bị đổi trạng thái cả ngày; trạng thái bàn chỉ chuyển `DANG_SU_DUNG` khi khách đã đến và được xếp bàn.
- Phục vụ chỉ xử lý lịch thuộc khu vực được phân công. Lịch chưa chọn khu vực được hiển thị cho mọi phục vụ, nhưng họ chỉ được chọn bàn thuộc khu vực của mình.
- Bàn thực tế phải trống, đủ sức chứa, không thuộc nhóm ghép và không có đơn mở.
- Đơn đầu tiên tạo tại bàn thực tế được tự động liên kết với lịch đặt bàn.
- Khi hóa đơn liên kết được thanh toán, lịch tự chuyển `HOAN_THANH`; nếu đơn liên kết bị hủy, lịch chuyển `DA_HUY`.
- Sau 15 phút kể từ giờ hẹn, nhân viên có thể đánh dấu `KHONG_DEN`.

## API khách hàng

### Danh sách khu vực

```http
GET /api/customer/reservations/areas
```

Frontend nên dùng danh sách này thay vì cho khách nhập tự do để yêu cầu được chuyển đúng khu vực phục vụ.

### Tạo yêu cầu

```http
POST /api/customer/reservations
Content-Type: application/json
```

```json
{
  "hoTenKhach": "Nguyễn Văn A",
  "soDienThoai": "0901234567",
  "ngayGioDen": "2026-07-10T18:30:00",
  "soLuongKhach": 4,
  "khuVucMongMuon": "Tầng 1 - Khu vực trong nhà",
  "thoiLuongPhut": 120,
  "ghiChu": "Cần ghế trẻ em"
}
```

Phản hồi trả `maTraCuu`, ví dụ `DB-7A1B2C3D4E`.

### Tra cứu

```http
GET /api/customer/reservations/{maTraCuu}?phone=0901234567
```

### Cập nhật

```http
PUT /api/customer/reservations/{maTraCuu}?phone=0901234567
Content-Type: application/json
```

Body giống API tạo. Nếu lịch đã xác nhận, việc đổi ngày/giờ sẽ đưa lịch về `CHO_XAC_NHAN` để nhà hàng xác nhận lại.

### Hủy

```http
POST /api/customer/reservations/{maTraCuu}/cancel?phone=0901234567
Content-Type: application/json
```

```json
{
  "reason": "Thay đổi kế hoạch"
}
```

## API admin/phục vụ

### Danh sách có phân trang

```http
GET /api/reservations?status=CHO_XAC_NHAN&from=2026-07-10&to=2026-07-10&page=0&size=10
Authorization: Bearer <token>
```

Bộ lọc: `status`, `from`, `to`, `keyword`, `area`, `page`, `size`.

### Danh sách bàn khả dụng

```http
GET /api/reservations/availability/tables?arrival=2026-07-10T18:30:00&partySize=4&durationMinutes=120&area=Tầng%201
Authorization: Bearer <token>
```

### Xác nhận và chọn bàn dự kiến

```http
POST /api/reservations/{id}/confirm
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "maBanDuKien": 3,
  "ghiChu": "Đã gọi điện xác nhận"
}
```

### Từ chối

```http
POST /api/reservations/{id}/reject
```

```json
{
  "reason": "Không còn bàn phù hợp trong khung giờ"
}
```

### Check-in

```http
POST /api/reservations/{id}/check-in
```

### Xếp bàn thực tế

```http
POST /api/reservations/{id}/assign-table
```

```json
{
  "maBan": 3
}
```

### Đánh dấu không đến

```http
POST /api/reservations/{id}/no-show
```

### Nhân viên hủy

```http
POST /api/reservations/{id}/cancel
```

```json
{
  "reason": "Khách gọi điện hủy"
}
```

## WebSocket

Các màn hình đặt bàn tải lại dữ liệu khi nhận sự kiện tại:

```text
/topic/reservations
/topic/admin/reservations
/topic/customer/reservations/{maTraCuu}
```

Sự kiện gồm:

```text
RESERVATION_CREATED
RESERVATION_UPDATED
RESERVATION_CONFIRMED
RESERVATION_REJECTED
RESERVATION_CHECKED_IN
RESERVATION_TABLE_ASSIGNED
RESERVATION_TABLE_TRANSFERRED
RESERVATION_ORDER_LINKED
RESERVATION_NO_SHOW
RESERVATION_CANCELLED
RESERVATION_COMPLETED
```
