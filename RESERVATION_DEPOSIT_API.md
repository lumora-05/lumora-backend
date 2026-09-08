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

### 2. Lấy QR cọc payOS

```http
GET /api/customer/reservations/{maTraCuu}/deposit/vietqr?phone=0901234567
```

Endpoint giữ nguyên URL/response để tương thích frontend, nhưng QR hiện được tạo bằng **payOS**. Mở QR chưa được xem là thanh toán; backend chỉ chuyển cọc sang `DA_THANH_TOAN` khi nhận webhook payOS hợp lệ.

## Luồng Thu ngân/Admin

Sau khi khách chuyển khoản thành công, payOS webhook tự xác nhận tiền cọc. Thu ngân/Admin **không kiểm tra giao dịch và không đánh dấu cọc thủ công** nữa.

Nhân viên chỉ chọn bàn dự kiến và xác nhận lịch sau khi `trangThaiCoc = DA_THANH_TOAN`:

```http
POST /api/reservations/{id}/confirm
Authorization: Bearer <token>
Content-Type: application/json
```

Endpoint tương thích cũ `POST /api/reservations/{id}/deposit/confirm-and-reservation` vẫn được giữ, nhưng cũng yêu cầu cọc đã được webhook payOS xác nhận trước. API `POST /api/reservations/{id}/deposit/confirm` không còn cho phép tự đánh dấu một cọc đang chờ thanh toán; nếu cọc đã `DA_THANH_TOAN` thì trả kết quả hiện tại theo kiểu idempotent.

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

Và để tạo bảng payment attempt payOS riêng cho tiền cọc đặt bàn, chạy thêm:

```text
database/payos_reservation_deposit_upgrade.sql
```

`reservation_deposit_upgrade.sql` cũng cập nhật constraint đặt bàn để hỗ trợ trạng thái `HET_HAN`.
