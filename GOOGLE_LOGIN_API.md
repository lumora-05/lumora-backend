# Đăng nhập Google cho nhân viên và khách hàng LUMORA

## Nguyên tắc

- Google chỉ xác minh danh tính và email.
- Hệ thống không tự tạo nhân viên mới từ tài khoản Google.
- Nếu email Google trùng nhân viên trong bảng `nhan_vien`, giữ nguyên luồng nhân viên.
- Nếu không trùng nhân viên, đăng nhập/tạo tài khoản khách hàng với vai trò `CUSTOMER`.
- Khách được nhận diện bằng `sub` của Google (`google_subject` duy nhất), không dùng email hay SĐT để tự ghép tài khoản.
- Khách mới chưa có SĐT và mật khẩu; `soDienThoai` trả về `null`. Không tạo SĐT giả.
- Khách có thể bổ sung SĐT qua `PUT /api/customer/account/me` với JWT khách hàng và `{ "hoTen": "...", "soDienThoai": "0901234567" }`. SĐT trùng tài khoản khác bị từ chối `409`.
- Khách Google đã bổ sung SĐT vẫn đăng nhập bằng Google; API đăng ký công khai không được đặt mật khẩu cho tài khoản này.
- Vai trò, trạng thái và JWT luôn lấy từ hệ thống LUMORA.
- Chỉ nhân viên có trạng thái `DANG_LAM_VIEC` mới được đăng nhập.

## Cấu hình

Thiết lập OAuth 2.0 Web Client ID bằng biến môi trường:

```bash
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
```

Frontend phải dùng đúng cùng Client ID này.

## API

```http
POST /api/auth/google
Content-Type: application/json
```

Request:

```json
{
  "credential": "GOOGLE_ID_TOKEN"
}
```

Response thành công giữ nguyên cấu trúc đăng nhập JWT hiện tại:

```json
{
  "success": true,
  "message": "Đăng nhập Google thành công",
  "data": {
    "token": "LUMORA_JWT",
    "username": "waiter01",
    "role": "WAITER",
    "fullName": "Nguyễn Văn A",
    "maNhanVien": 12,
    "anhDaiDien": null
  }
}
```

## Các trường hợp từ chối

- Google credential không hợp lệ hoặc hết hạn: `401`.
- Email Google chưa xác minh: `401`.
- Khách hàng đã ngừng hoạt động: `403`.
- Nhân viên đã ngừng hoạt động: `403`.
- Backend chưa cấu hình `GOOGLE_CLIENT_ID`: `503`.

## Phản hồi khách hàng

API và request không đổi: `POST /api/auth/google` với `{ "credential": "GOOGLE_ID_TOKEN" }`.
Trường `data` cho khách mới:

```json
{
  "token": "CUSTOMER_JWT",
  "username": "customer:42",
  "role": "CUSTOMER",
  "fullName": "Nguyễn Văn An",
  "maNhanVien": null,
  "anhDaiDien": null,
  "maKhachHang": 42,
  "soDienThoai": null,
  "diemTichLuy": 0
}
```

JWT giữ `tokenType=CUSTOMER`, `customerId`; subject dùng `customer:<id>` khi chưa có SĐT.
Khách Google không có quyền API nhân viên. Tài khoản nhân viên bị khóa không được chuyển sang nhánh khách hàng.
Ràng buộc duy nhất trong DB ngăn tạo trùng tài khoản Google; nếu hai yêu cầu đầu tiên đồng thời gây `409`, đăng nhập lại để lấy tài khoản đã tạo.

## Cơ sở dữ liệu và frontend

- Giữ nguyên `GOOGLE_CLIENT_ID` đang dùng, frontend dùng cùng Client ID.
- Mặc định `JPA_DDL_AUTO=update`: Hibernate thêm `google_subject`, `google_email` và ràng buộc duy nhất; lúc khởi động gỡ NOT NULL của SĐT. Không xóa/ghép dữ liệu cũ.
- Nếu dùng `validate`/`none`, chạy `database/customer_google_login_upgrade.sql` trước khi khởi động.
- Frontend lưu phiên khách khi `role=CUSTOMER`, lấy tên từ `fullName`, cho phép SĐT trống và chuyển về trang khách hàng (hoặc `next` hợp lệ).
- Khi đặt hàng vẫn nhập SĐT người nhận theo kiểm tra sẵn có; muốn lưu vào hồ sơ dùng API `PUT .../me` ở trên.
- Bản cập nhật này chỉ sửa backend; frontend cần được kiểm tra/cập nhật tiếp cho các bước trên.

## Kiểm thử

Chạy `mvn test` với Java 21. Các test mới kiểm tra tạo/đăng nhập lại khách Google, token khách không có SĐT, khóa tài khoản, ngăn đăng ký chiếm tài khoản Google, cập nhật SĐT và giữ nguyên quyền nhân viên.

Kết quả kiểm tra bản cập nhật: biên dịch Java 21 thành công; 22/22 kiểm thử đạt (13 mới, 9 sẵn có). Kiểm thử Google dùng payload giả lập sau bước xác minh, chưa đăng nhập Google thật hoặc chạy migration trên DB triển khai. Môi trường kiểm thử nạp Byte Buddy agent khi khởi động JVM do không hỗ trợ tự attach.

Sau khi thay mã nguồn, chạy `mvn clean test` rồi build/khởi động lại để không dùng các lớp đã biên dịch từ bản cũ trong `target`.
