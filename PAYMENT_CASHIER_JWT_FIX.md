# Cập nhật xác định thu ngân từ JWT

## Thay đổi

- `POST /api/payments` lấy tài khoản thực hiện thanh toán từ `Principal`/JWT.
- Backend không còn tin cậy `maNhanVien` do frontend gửi để xác định thu ngân.
- `maNhanVien` trong `PaymentRequest` được giữ tùy chọn để tương thích frontend cũ.
- Phản hồi đăng nhập bổ sung `maNhanVien` để frontend hiện tại không còn báo không xác định được nhân viên.
- Backend kiểm tra tài khoản thanh toán có vai trò `CASHIER` hoặc `ADMIN` và đang làm việc.

## Sau khi thay backend

1. Dừng và chạy lại Spring Boot.
2. Đăng xuất frontend.
3. Xóa dữ liệu đăng nhập cũ bằng `localStorage.clear()`.
4. Đăng nhập lại tài khoản thu ngân rồi thử thanh toán.
