# Restaurant Backend - Spring Boot

Backend được viết theo tài liệu báo cáo đồ án **Xây dựng hệ thống quản lý đơn cho nhà hàng**.

## 1. Công nghệ

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security + JWT
- PostgreSQL
- Lombok

## 2. Cấu hình database

Tạo database PostgreSQL:

```sql
CREATE DATABASE restaurant_db;
```

Sửa file:

```text
src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/restaurant_db
spring.datasource.username=postgres
spring.datasource.password=123456
```

Chạy backend:

```bash
mvn spring-boot:run
```

Lần chạy đầu hệ thống tự tạo dữ liệu mẫu:

```text
username: admin
password: 123456
role: ADMIN
```

## 3. Luồng API chính

### Đăng nhập

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

Lấy token trả về và gửi ở header:

```http
Authorization: Bearer <token>
```

### Khách hàng xem thực đơn

```http
GET /api/menu
GET /api/menu/1
GET /api/menu/category/1
```

### Khách hàng gửi đơn hàng

```http
POST /api/orders
Content-Type: application/json

{
  "maBan": 1,
  "ghiChu": "Ít cay",
  "items": [
    { "maMonAn": 1, "soLuong": 2, "ghiChu": "Không hành" },
    { "maMonAn": 2, "soLuong": 1, "ghiChu": "" }
  ]
}
```

### Nhân viên phục vụ xác nhận đơn

```http
PUT /api/orders/1/status
Authorization: Bearer <token>
Content-Type: application/json

{
  "trangThai": "DA_XAC_NHAN",
  "maNhanVien": 1
}
```

### Nhân viên bếp cập nhật trạng thái món

```http
PUT /api/orders/items/1/status
Authorization: Bearer <token>
Content-Type: application/json

{
  "trangThaiMon": "DANG_CHE_BIEN"
}
```

Các trạng thái món hợp lệ:

```text
CHO_BEP, DANG_CHE_BIEN, HOAN_THANH, HET_MON
```

### Thu ngân tạo hóa đơn

```http
POST /api/payments
Authorization: Bearer <token>
Content-Type: application/json

{
  "maDonHang": 1,
  "maNhanVien": 1,
  "phuongThucThanhToan": "TIEN_MAT"
}
```

### Admin xem báo cáo doanh thu

```http
GET /api/payments/revenue?from=2026-06-01&to=2026-06-30
Authorization: Bearer <token>
```
### Admin upload ảnh món ăn

```http
POST /api/uploads/foods
Authorization: Bearer <token>
Content-Type: multipart/form-data

file = ảnh món ăn
```

API sẽ trả về đường dẫn ảnh:

```json
{
  "url": "/uploads/foods/ten-file.jpg"
}
```

Khi thêm hoặc sửa món ăn, truyền đường dẫn đó vào trường `hinhAnh`:

```json
{
  "maDanhMuc": 1,
  "tenMonAn": "Phở bò",
  "gia": 45000,
  "moTa": "Phở bò truyền thống",
  "hinhAnh": "/uploads/foods/ten-file.jpg",
  "trangThai": true
}
```

Frontend hiển thị ảnh bằng URL:

```text
http://localhost:8080/uploads/foods/ten-file.jpg
```


## 4. Endpoint quản trị

```text
POST   /api/categories
PUT    /api/categories/{id}
DELETE /api/categories/{id}

POST   /api/menu
PUT    /api/menu/{id}
DELETE /api/menu/{id}

POST   /api/tables
PUT    /api/tables/{id}
DELETE /api/tables/{id}

POST   /api/employees
PUT    /api/employees/{id}
DELETE /api/employees/{id}
```

## 5. Ghi chú thiết kế

- `trang_thai` của nhân viên lưu dạng `String`: `DANG_LAM_VIEC`, `NGHI_PHEP`, `NGHI_LAM_VIEC` để code đơn giản hơn trong phạm vi đồ án.
- Các bảng vẫn giữ tên tiếng Việt theo báo cáo: `nhan_vien`, `ban_an`, `danh_muc_mon`, `mon_an`, `don_hang`, `chi_tiet_don_hang`, `hoa_don`.
- Ảnh món ăn lưu trong thư mục `uploads/foods`; database chỉ lưu đường dẫn ảnh ở cột `hinh_anh`.


## 6. Ghi chú sau chỉnh sửa

- Package `exception` đã được bỏ để cấu trúc đơn giản hơn.
- Package `enums` đã được bỏ; các trạng thái hiện lưu trực tiếp bằng kiểu `String` trong Entity/DTO/Service.
- Các lỗi như không tìm thấy dữ liệu hoặc request sai hiện dùng trực tiếp `ResponseStatusException` của Spring Boot trong tầng Service.

- Đã thêm Lombok để giảm getter/setter trong Entity.
- Đã thêm thư mục `uploads/foods` để lưu ảnh món ăn.
- Đã thêm API `POST /api/uploads/foods` để admin upload ảnh món ăn.

## Thông báo thành công CRUD

Các API thêm/sửa/xóa hiện trả về `message` để Postman hoặc frontend hiển thị thông báo.

Ví dụ thêm món ăn thành công:

```json
{
  "message": "Thêm món ăn thành công",
  "data": {
    "maMonAn": 1,
    "tenMonAn": "Phở bò"
  }
}
```

Ví dụ xóa món ăn thành công:

```json
{
  "message": "Xóa món ăn thành công"
}
```

Các API đã thêm thông báo:

- Danh mục món ăn: thêm, sửa, xóa
- Món ăn: thêm, sửa, xóa
- Bàn ăn: thêm, sửa, xóa
- Nhân viên: thêm, sửa, xóa
- Đơn hàng: gửi đơn, cập nhật trạng thái đơn, cập nhật trạng thái món
- Thanh toán: tạo hóa đơn
- Upload ảnh món ăn

Thông báo lỗi vẫn được xử lý trong Service bằng `ResponseStatusException`.

## Định dạng phản hồi API

Các API đã được chuẩn hóa phản hồi để frontend/Postman dễ xử lý thông báo:

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "data": {}
}
```

Khi xảy ra lỗi, hệ thống trả về dạng:

```json
{
  "success": false,
  "message": "Nội dung lỗi",
  "data": null
}
```

Ví dụ đăng nhập thành công:

```json
{
  "success": true,
  "message": "Đăng nhập thành công",
  "data": {
    "token": "...",
    "username": "admin",
    "role": "ADMIN",
    "fullName": "Quản trị viên"
  }
}
```

Ví dụ lỗi đăng nhập:

```json
{
  "success": false,
  "message": "Tên đăng nhập hoặc mật khẩu không đúng",
  "data": null
}
```

## 4. Chức năng mã QR bàn ăn

Backend đã hỗ trợ tạo ảnh QR cho từng bàn ăn bằng thư viện ZXing.

Cấu hình link frontend trong file:

```properties
app.frontend.base-url=http://localhost:5173
app.upload.qr-dir=uploads/qrcodes
app.qr.size=300
```

Khi thêm hoặc cập nhật bàn ăn, hệ thống tự tạo:

```text
maQr  = /menu?tableId={maBan}
anhQr = /uploads/qrcodes/table-{maBan}.png
```

Tạo lại ảnh QR cho một bàn:

```http
POST /api/tables/1/qr
Authorization: Bearer <token admin>
```

Kết quả trả về dạng:

```json
{
  "success": true,
  "message": "Tạo mã QR cho bàn ăn thành công",
  "data": {
    "maBan": 1,
    "tenBan": "Bàn 1",
    "maQr": "/menu?tableId=1",
    "anhQr": "/uploads/qrcodes/table-1.png"
  }
}
```

Xem hoặc in ảnh QR bằng URL:

```text
http://localhost:8080/uploads/qrcodes/table-1.png
```

Khi deploy thật, đổi `app.frontend.base-url` thành domain frontend thật, ví dụ:

```properties
app.frontend.base-url=https://nhahangabc.com
```

## Các API đã bổ sung cho đồ án

### Dashboard Admin
- `GET /api/dashboard` - Tổng quan số bàn, đơn hôm nay, doanh thu hôm nay, doanh thu 7 ngày gần nhất.
- `GET /api/dashboard/revenue/last-7-days` - Dữ liệu biểu đồ doanh thu 7 ngày.

### Khuyến mãi
- `GET /api/promotions/active` - Danh sách khuyến mãi đang áp dụng cho khách/nhân viên.
- `GET /api/promotions` - Danh sách toàn bộ khuyến mãi. Quyền: ADMIN.
- `GET /api/promotions/{id}` - Chi tiết khuyến mãi. Quyền: ADMIN.
- `POST /api/promotions` - Thêm khuyến mãi. Quyền: ADMIN.
- `PUT /api/promotions/{id}` - Cập nhật khuyến mãi. Quyền: ADMIN.
- `DELETE /api/promotions/{id}` - Tắt khuyến mãi theo soft delete. Quyền: ADMIN.
- `POST /api/promotions/apply` - Áp dụng mã khuyến mãi cho đơn hàng. Quyền: ADMIN, CASHIER, WAITER.

Ví dụ body thêm khuyến mãi:
```json
{
  "maCode": "SALE10",
  "tenKhuyenMai": "Giảm 10% hóa đơn",
  "loaiGiam": "PERCENT",
  "giaTriGiam": 10,
  "ngayBatDau": "2026-06-01",
  "ngayKetThuc": "2026-06-30",
  "trangThai": true
}
```

Ví dụ body áp dụng khuyến mãi:
```json
{
  "maDonHang": 1,
  "maCode": "SALE10"
}
```

### Khách hàng quét QR
- `GET /api/customer/tables/{tableId}` - Khách lấy thông tin bàn, danh mục và thực đơn đang bán.
- `POST /api/customer/orders` - Khách gửi đơn hàng.
- `GET /api/customer/orders/{orderId}` - Khách theo dõi trạng thái đơn.

Các API `/api/customer/**`, `/api/menu/**`, `/api/categories/active`, `/api/promotions/active` được mở public để khách dùng sau khi quét QR.

## Đồng bộ trang Bàn & QR của frontend

Backend đã được bổ sung để khớp giao diện hai tab **Quản lý bàn** và **Quản lý QR**.

### Dữ liệu bàn trả về

```json
{
  "maBan": 2,
  "tenBan": "Bàn 02",
  "khuVuc": "Tầng 1 - Khu vực trong nhà",
  "sucChua": 4,
  "trangThai": "DANG_SU_DUNG",
  "ghiChu": null,
  "maQr": "QR0002",
  "anhQr": "/uploads/qrcodes/table-2-1719999999999.png",
  "trangThaiQr": "DANG_HOAT_DONG",
  "ngayTaoQr": "2026-06-19T10:30:00",
  "ngayCapNhatQr": "2026-06-19T10:30:00"
}
```

### API bàn và QR

| Phương thức | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/tables` | Danh sách bàn và thông tin QR |
| GET | `/api/tables/{id}` | Chi tiết một bàn |
| POST | `/api/tables` | Thêm bàn, chưa tự tạo QR |
| PUT | `/api/tables/{id}` | Cập nhật bàn, không tự tạo lại QR |
| DELETE | `/api/tables/{id}` | Xóa bàn và file QR liên quan |
| POST | `/api/tables/{id}/qr` | Tạo hoặc tạo lại QR |
| PATCH | `/api/tables/{id}/qr/status` | Đổi trạng thái QR |
| GET | `/api/customer/tables/{id}` | Khách mở menu từ QR đang hoạt động |

Body cập nhật trạng thái QR:

```json
{
  "trangThaiQr": "TAM_NGUNG"
}
```

Giá trị hợp lệ: `DANG_HOAT_DONG`, `TAM_NGUNG`, `NGUNG_SU_DUNG`.

Trạng thái bàn hợp lệ: `TRONG`, `DANG_SU_DUNG`, `DAT_TRUOC`, `DANG_DON_DEP`, `BAO_TRI`, `DANG_THANH_TOAN`.

QR mã hóa đúng route frontend: `http://localhost:5173/table/{maBan}`. Khi frontend chạy cổng khác, đặt biến môi trường trước khi chạy backend:

```bash
APP_FRONTEND_BASE_URL=http://localhost:5174
```
