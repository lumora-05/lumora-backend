# API cọc đặt bàn

## Chính sách mặc định

- Cọc cố định mỗi lượt đặt bàn: `100000` đồng, không phụ thuộc số người.
- Thời gian thanh toán cọc: `10` phút kể từ khi tạo yêu cầu.
- Khách hủy trước giờ đến ít nhất `120` phút: cọc chuyển `CHO_HOAN`.
- Khách hủy sát giờ hoặc không đến: `MAT_COC`.
- Nhà hàng từ chối/hủy lịch đã cọc: `CHO_HOAN`.
- Khi thanh toán hóa đơn tại bàn, cọc được trừ vào số tiền còn phải trả. Hóa đơn vẫn giữ tổng giá trị bán hàng và lưu riêng `tienCocDaKhauTru`.

Các giá trị trên có thể chỉnh qua API Cài đặt hệ thống bằng:

- `reservationDepositAmount`
- `reservationDepositPaymentTimeoutMinutes`
- `reservationDepositRefundAdvanceMinutes`

## Trạng thái cọc

```text
CHO_THANH_TOAN
  -> DA_THANH_TOAN
      -> DA_KHAU_TRU
      -> CHO_HOAN -> DA_HOAN
      -> MAT_COC
  -> DA_HUY
```

## Luồng khách hàng

### 1. Tạo đặt bàn

```http
POST /api/customer/reservations
```

Phản hồi `ReservationResponse` có thêm:

- `tienCoc`
- `trangThaiCoc`
- `tienCocDaKhauTru`
- `thoiHanThanhToanCoc`
- `thoiGianThanhToanCoc`
- `thoiGianHoanCoc`
- `lyDoXuLyCoc`

### 2. Lấy VietQR cọc

```http
GET /api/customer/reservations/{maTraCuu}/deposit/vietqr?phone=0901234567
```

Endpoint chỉ tạo QR, **không tự đánh dấu đã thanh toán**.

## Luồng Thu ngân/Admin

### Xác nhận cọc và đặt bàn trong một thao tác (khuyến nghị)

Sau khi Thu ngân/Admin đã kiểm tra tiền thực tế vào tài khoản và chọn bàn dự kiến, gọi:

```http
POST /api/reservations/{id}/deposit/confirm-and-reservation
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "maBanDuKien": 6,
  "ghiChu": "Bàn gần cửa sổ"
}
```

Backend xử lý trong **một transaction**: xác nhận cọc, lưu người/thời gian xác nhận cọc, kiểm tra và giữ bàn dự kiến, rồi chuyển lịch sang `DA_XAC_NHAN`. Nếu bàn không còn khả dụng hoặc bất kỳ bước nào thất bại thì toàn bộ thao tác được rollback, không có trạng thái xác nhận nửa chừng.

Nếu cọc đã được xác nhận trước đó bằng API cũ nhưng lịch vẫn `CHO_XAC_NHAN`, endpoint này vẫn có thể dùng để hoàn tất bước chọn bàn và xác nhận lịch mà không ghi đè lịch sử xác nhận cọc.

### API xác nhận cọc riêng (giữ để tương thích)

```http
POST /api/reservations/{id}/deposit/confirm
Authorization: Bearer <token>
```

Endpoint cũ vẫn được giữ để không ảnh hưởng client hiện tại. Nó chỉ xác nhận cọc; sau đó client cũ tiếp tục gọi `POST /api/reservations/{id}/confirm`. Frontend mới nên ưu tiên endpoint gộp ở trên để tránh bắt Thu ngân thao tác hai lần.

### Ghi nhận đã hoàn cọc

Khi lịch có `trangThaiCoc = CHO_HOAN`:

```http
POST /api/reservations/{id}/deposit/refund
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "reason": "Đã hoàn cọc qua chuyển khoản"
}
```

## Hết hạn tự động

Scheduler chạy mỗi phút. Yêu cầu `CHO_XAC_NHAN` + cọc `CHO_THANH_TOAN` quá `thoiHanThanhToanCoc` sẽ tự chuyển:

- đặt bàn: `HET_HAN`
- cọc: `DA_HUY`

## Database

Nếu production không dùng `spring.jpa.hibernate.ddl-auto=update`, chạy:

```text
database/reservation_deposit_upgrade.sql
```

Script cũng cập nhật constraint đặt bàn để hỗ trợ trạng thái `HET_HAN`.
