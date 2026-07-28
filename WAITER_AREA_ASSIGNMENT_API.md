# Phân công khu vực cho nhân viên phục vụ

## Dữ liệu nhân viên

API thêm/cập nhật nhân viên giữ nguyên endpoint cũ và nhận thêm trường tùy chọn:

```json
{
  "hoTen": "Nguyễn Văn A",
  "soDienThoai": "0900000000",
  "email": "waiter@example.com",
  "tenDangNhap": "waiter01",
  "matKhau": "******",
  "tenVaiTro": "WAITER",
  "trangThai": "DANG_LAM_VIEC",
  "khuVucPhuTrach": "Khu vực A"
}
```

- `khuVucPhuTrach` chỉ được lưu với vai trò `WAITER`.
- Khi đổi nhân viên sang vai trò khác, backend tự xóa khu vực phụ trách.
- Client cũ không gửi trường này khi cập nhật thì backend giữ nguyên giá trị hiện có.
- Nhân viên phục vụ chưa được gán khu vực sẽ nhận danh sách bàn/đơn rỗng và không thể thao tác đơn.

## Lọc tự động theo JWT

Không đổi endpoint frontend hiện tại:

- `GET /api/tables`
- `GET /api/tables/{id}`
- `GET /api/orders`
- `GET /api/orders/page`
- `GET /api/orders/status/{status}`
- `GET /api/orders/{id}`

Khi tài khoản có vai trò `WAITER`, backend tự lấy `khuVucPhuTrach` từ nhân viên đăng nhập và chỉ trả bàn/đơn có `ban_an.khu_vuc` tương ứng.

Admin, bếp và thu ngân vẫn giữ phạm vi dữ liệu cũ.

## Kiểm tra quyền thao tác

Backend cũng kiểm tra khu vực trước khi phục vụ thực hiện:

- Tạo đơn hoặc gọi thêm món.
- Xác nhận đơn mới.
- Xác nhận món đã phục vụ.
- Gửi yêu cầu thanh toán.

Frontend không thể truyền mã nhân viên hoặc tên khu vực khác để vượt quyền vì backend luôn lấy nhân viên từ JWT.
