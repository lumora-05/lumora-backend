# Đăng nhập Google cho nhân viên LUMORA

## Nguyên tắc

- Google chỉ xác minh danh tính và email.
- Hệ thống không tự tạo nhân viên mới từ tài khoản Google.
- Email Google phải trùng với email của nhân viên trong bảng `nhan_vien`.
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
- Email chưa tồn tại trong danh sách nhân viên: `403`.
- Nhân viên đã ngừng hoạt động: `403`.
- Backend chưa cấu hình `GOOGLE_CLIENT_ID`: `503`.
