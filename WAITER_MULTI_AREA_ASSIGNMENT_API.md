# Phân công nhiều khu vực cho nhân viên phục vụ

Backend hỗ trợ một nhân viên `WAITER` phụ trách một hoặc nhiều khu vực.

## Tương thích dữ liệu cũ

- `khuVucPhuTrach`: trường cũ, vẫn được giữ để frontend cũ tiếp tục hoạt động.
- `danhSachKhuVucPhuTrach`: trường mới, chứa toàn bộ khu vực được phân công.
- Khi API mới gửi `danhSachKhuVucPhuTrach`, backend ưu tiên danh sách này và đồng bộ khu vực đầu tiên về `khuVucPhuTrach`.
- Khi client cũ chỉ gửi `khuVucPhuTrach`, backend vẫn hoạt động và lưu thành danh sách một phần tử.

## Ví dụ cập nhật nhân viên phục vụ

```json
{
  "hoTen": "Phục vụ 2",
  "soDienThoai": "09793874190",
  "email": "phucvu2@gmail.com",
  "tenDangNhap": "phucvu2",
  "matKhau": "",
  "tenVaiTro": "WAITER",
  "trangThai": "DANG_LAM_VIEC",
  "danhSachKhuVucPhuTrach": [
    "Tầng 1 - Khu A",
    "Tầng 1 - Khu B"
  ]
}
```

## Phạm vi quyền của phục vụ

Các API bàn, đơn hàng, yêu cầu phục vụ, đặt bàn và thao tác chuyển/ghép bàn sẽ cho phép truy cập nếu khu vực của bàn nằm trong `danhSachKhuVucPhuTrach`.

Ví dụ:

- Phục vụ A: `Tầng 1 - Khu A`
- Phục vụ B: `Tầng 1 - Khu A`, `Tầng 1 - Khu B`

Khi A tạm nghỉ, Admin chỉ cần gán thêm `Tầng 1 - Khu A` cho B. B vẫn giữ quyền ở `Tầng 1 - Khu B`.

## Database

Chạy `database/waiter_multi_area_assignment_upgrade.sql` nếu môi trường triển khai không dùng `spring.jpa.hibernate.ddl-auto=update` hoặc nếu muốn migrate dữ liệu cũ chủ động.
