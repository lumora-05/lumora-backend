# API đổi mật khẩu tài khoản

## Endpoint

```http
PUT /api/tai-khoan/doi-mat-khau
Authorization: Bearer <token>
Content-Type: application/json
```

## Request

```json
{
  "matKhauHienTai": "MatKhauCu@123",
  "matKhauMoi": "MatKhauMoi@456",
  "xacNhanMatKhauMoi": "MatKhauMoi@456"
}
```

## Quy tắc mật khẩu mới

- Từ 8 đến 72 ký tự.
- Có ít nhất một chữ hoa, một chữ thường, một chữ số và một ký tự đặc biệt.
- Không chứa khoảng trắng.
- Không được giống mật khẩu hiện tại.
- Phải trùng với trường xác nhận mật khẩu.

## Response thành công

```json
{
  "success": true,
  "message": "Đổi mật khẩu thành công",
  "data": null
}
```

Mật khẩu được kiểm tra và mã hóa bằng BCrypt trước khi lưu. Entity và database không cần thêm cột mới vì tiếp tục sử dụng trường `mat_khau` hiện có.
