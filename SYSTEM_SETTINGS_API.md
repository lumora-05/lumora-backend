# System Settings API

Chức năng Cài đặt hệ thống dành cho Admin. Cấu hình được lưu trong PostgreSQL; logo và banner được upload lên Cloudinary và database chỉ lưu URL.

## Public

### GET `/api/system-settings/public`
Không cần đăng nhập. Frontend công khai dùng endpoint này để lấy thông tin nhà hàng, `logoUrl` và `bannerUrl`.

## Admin

Tất cả endpoint dưới đây yêu cầu JWT có role `ADMIN`.

### GET `/api/system-settings`
Lấy cấu hình hiện tại.

### PUT `/api/system-settings`
JSON mẫu:
```json
{
  "restaurantName": "LUMORA",
  "address": "139 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng",
  "phone": "0900000000",
  "email": "contact@lumora.food",
  "openingHours": "07:00 - 22:00",
  "reservationUrl": "/reservations",
  "menuUrl": "/#menu"
}
```

### POST `/api/system-settings/logo`
`multipart/form-data`, field `file`. Tối đa 5 MB; hỗ trợ jpg/jpeg/png/webp/gif.

### POST `/api/system-settings/banner`
`multipart/form-data`, field `file`. Tối đa 5 MB; hỗ trợ jpg/jpeg/png/webp/gif.

### DELETE `/api/system-settings/logo`
Xóa logo tùy chỉnh hiện tại và dọn ảnh Cloudinary nếu có.

### DELETE `/api/system-settings/banner`
Xóa banner tùy chỉnh hiện tại và dọn ảnh Cloudinary nếu có.

## Tương thích cấu hình cũ

Lần đầu bảng chưa có dữ liệu, backend khởi tạo bản ghi từ `app.restaurant.*` hiện có. Sau đó database là nguồn cấu hình chính. Backend đồng bộ lại `RestaurantInfoProperties` khi khởi động/cập nhật để chatbot tiếp tục dùng thông tin nhà hàng mới mà không cần sửa các service chatbot hiện tại.
