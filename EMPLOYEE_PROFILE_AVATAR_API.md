# API hồ sơ và ảnh đại diện nhân viên

## Cấu trúc dữ liệu

Backend bổ sung cột `nhan_vien.anh_dai_dien` để lưu đường dẫn ảnh, ví dụ:

```text
/uploads/avatars/550e8400-e29b-41d4-a716-446655440000.webp
```

Khi dùng `spring.jpa.hibernate.ddl-auto=update`, Hibernate tự tạo cột. Có thể chạy thủ công file:

```text
database/employee_profile_avatar_upgrade.sql
```

## API

Tất cả API dưới đây yêu cầu JWT hợp lệ.

### Xem hồ sơ

```http
GET /api/tai-khoan/ho-so
```

### Cập nhật thông tin cá nhân

```http
PUT /api/tai-khoan/ho-so
Content-Type: application/json

{
  "hoTen": "Nguyễn Văn Bếp",
  "email": "bep@example.com",
  "soDienThoai": "0901234567"
}
```

Không có trường địa chỉ.

### Upload hoặc đổi ảnh đại diện

```http
POST /api/tai-khoan/ho-so/anh-dai-dien
Content-Type: multipart/form-data
file: <image>
```

Hỗ trợ JPG, JPEG, PNG, WebP và GIF; tối đa 5 MB.

### Xóa ảnh đại diện

```http
DELETE /api/tai-khoan/ho-so/anh-dai-dien
```

Ảnh được phục vụ công khai qua `/uploads/avatars/{fileName}`. Đường dẫn ảnh cũng được trả trong phản hồi đăng nhập qua trường `anhDaiDien`.
