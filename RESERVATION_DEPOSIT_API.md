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

### Xác nhận đã nhận cọc

```http
POST /api/reservations/{id}/deposit/confirm
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "maGiaoDich": "FT260828123456"
}
```

Chỉ `ADMIN` hoặc `CASHIER` được gọi. Mã giao dịch cọc không được trùng mã đã dùng cho cọc hoặc hóa đơn.

Sau khi cọc được xác nhận, Thu ngân mới có thể gọi:

```http
POST /api/reservations/{id}/confirm
```

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
