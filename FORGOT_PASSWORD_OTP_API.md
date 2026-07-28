# Quên mật khẩu bằng mã OTP email

## 1. Gửi mã OTP

`POST /api/auth/forgot-password/send-code`

```json
{
  "email": "nhanvien@gmail.com"
}
```

Phản hồi luôn dùng thông báo chung để không làm lộ email có tồn tại hay không. OTP gồm 6 chữ số, mặc định hết hạn sau 10 phút và chỉ gửi lại sau 60 giây.

```json
{
  "success": true,
  "message": "Nếu email tồn tại trong hệ thống, mã xác nhận đã được gửi",
  "data": {
    "expiresInSeconds": 600,
    "resendAfterSeconds": 60
  }
}
```

## 2. Xác minh OTP

`POST /api/auth/forgot-password/verify-code`

```json
{
  "email": "nhanvien@gmail.com",
  "code": "123456"
}
```

Phản hồi thành công:

```json
{
  "success": true,
  "message": "Xác nhận mã thành công",
  "data": {
    "resetToken": "token-tam-thoi",
    "expiresInSeconds": 600
  }
}
```

OTP chỉ được nhập sai tối đa 5 lần. Sau khi xác minh thành công, OTP không thể sử dụng lại.

## 3. Đặt mật khẩu mới

`POST /api/auth/forgot-password/reset`

```json
{
  "resetToken": "token-tam-thoi",
  "matKhauMoi": "NewPassword@123",
  "xacNhanMatKhauMoi": "NewPassword@123"
}
```

Mật khẩu mới phải có 8-72 ký tự, chữ hoa, chữ thường, số, ký tự đặc biệt và không chứa khoảng trắng.

## Cấu hình Gmail SMTP

Giữ bí mật trong biến môi trường:

```text
MAIL_ENABLED=true
MAIL_FROM=your-email@gmail.com
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-google-app-password
```

Khi `MAIL_ENABLED=false`, OTP được ghi trong log backend để kiểm thử local.
