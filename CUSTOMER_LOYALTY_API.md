# API KHÁCH HÀNG THÂN THIẾT VÀ TÍCH ĐIỂM

## 1. Chính sách mặc định

- Thanh toán đủ `10.000đ` nhận `1 điểm`.
- `1 điểm` giảm `1.000đ`.
- Cần dùng tối thiểu `20 điểm` mỗi lần đổi.
- Tiền giảm bằng điểm không vượt quá `20%` số tiền sau khuyến mãi.
- Chỉ cộng/trừ điểm trong transaction xác nhận thanh toán cuối cùng.
- Không nhập số điện thoại: thanh toán như cũ, không tích điểm.

## 2. API quản lý khách hàng

Các API yêu cầu JWT của `ADMIN` hoặc `CASHIER`, trừ điều chỉnh điểm chỉ dành cho `ADMIN`.

### Chính sách điểm

```http
GET /api/loyalty/policy
```

### Tìm khách theo số điện thoại

```http
GET /api/loyalty/customers/by-phone?phone=0979792909
```

### Danh sách khách hàng

```http
GET /api/loyalty/customers?page=0&size=20&keyword=0979
```

### Tạo khách hàng

```http
POST /api/loyalty/customers
Content-Type: application/json

{
  "hoTen": "Nguyễn Văn A",
  "soDienThoai": "0979792909"
}
```

### Cập nhật khách hàng

```http
PUT /api/loyalty/customers/{customerId}
Content-Type: application/json

{
  "hoTen": "Nguyễn Văn A",
  "soDienThoai": "0979792909"
}
```

### Lịch sử điểm

```http
GET /api/loyalty/customers/{customerId}/transactions?page=0&size=20
```

`soDiem` dương là cộng, âm là trừ. Các loại gồm `EARN`, `REDEEM`, `ADJUST`.

### Admin điều chỉnh điểm

```http
POST /api/loyalty/customers/{customerId}/adjust-points
Content-Type: application/json

{
  "soDiem": 20,
  "lyDo": "Tặng điểm chăm sóc khách hàng"
}
```

Dùng số âm để trừ điểm. Backend không cho số dư xuống dưới 0.

## 3. Xem trước khi thanh toán

```http
GET /api/payments/loyalty-preview/order/{orderId}?phone=0979792909&pointsToUse=20
```

Kết quả gồm điểm hiện có, tối đa có thể dùng, tiền giảm, tổng thanh toán và điểm dự kiến được cộng.

## 4. Xác nhận thanh toán có tích điểm

API cũ được giữ nguyên và bổ sung 3 trường tùy chọn:

```http
POST /api/payments
Content-Type: application/json
Authorization: Bearer <cashier-token>

{
  "maDonHang": 15,
  "phuongThucThanhToan": "TIEN_MAT",
  "tienKhachDua": 500000,
  "maGiaoDich": null,
  "ghiChu": null,
  "soDienThoaiKhachHang": "0979792909",
  "hoTenKhachHang": "Nguyễn Văn A",
  "diemSuDung": 20
}
```

Nếu số điện thoại chưa tồn tại, backend tự tạo khách hàng. Khách mới chưa có điểm nên `diemSuDung` phải bằng 0.

Khi thanh toán thành công, backend đồng thời:

1. Tạo hóa đơn.
2. Gắn khách hàng vào đơn và hóa đơn.
3. Trừ điểm đã sử dụng.
4. Cộng điểm từ số tiền thực trả.
5. Cập nhật tổng chi tiêu.
6. Lưu lịch sử điểm.

Nếu một bước lỗi, toàn bộ transaction được rollback.

## 5. VietQR sau khi đổi điểm

API VietQR cũ vẫn dùng được. Khi khách đổi điểm, truyền thêm số điện thoại và điểm để QR có đúng số tiền cuối cùng:

```http
GET /api/payments/vietqr/order/{orderId}?phone=0979792909&pointsToUse=20
```

## 6. Trường dữ liệu mới trên đơn và hóa đơn

```text
khachHang
diemDaSuDung
tienGiamTuDiem
diemDuocCong
```

`tongTien` sau thanh toán là số tiền thực nhận sau khuyến mãi và sau giảm bằng điểm.

## 7. Nâng cấp cơ sở dữ liệu

Có thể để `spring.jpa.hibernate.ddl-auto=update`, hoặc chạy thủ công:

```text
database/customer_loyalty_upgrade.sql
```
