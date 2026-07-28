# Cloudinary cho ảnh LUMORA

Backend đã được chuyển để lưu các file mới trên Cloudinary:

- Ảnh món ăn: `lumora/foods`
- Ảnh đại diện: `lumora/avatars`
- Mã QR bàn: `lumora/qrcodes`

## Biến môi trường bắt buộc trên Render

```text
CLOUDINARY_CLOUD_NAME=<Cloud name>
CLOUDINARY_API_KEY=<API key>
CLOUDINARY_API_SECRET=<API secret>
```

Không đưa API Secret vào frontend, `application.properties` hoặc GitHub.

## Hoạt động sau khi triển khai

- `POST /api/uploads/foods` trả về URL HTTPS Cloudinary.
- Cập nhật avatar trả về URL HTTPS Cloudinary.
- Tạo hoặc tạo lại QR bàn sẽ tạo PNG trong bộ nhớ rồi tải lên Cloudinary.
- Khi thay avatar, thay ảnh món hoặc xóa món, backend dọn ảnh Cloudinary cũ sau khi transaction database thành công.
- Khi tạo lại hoặc xóa QR, backend dọn ảnh QR cũ.

## Dữ liệu cũ

Backend vẫn giữ khả năng đọc và dọn đường dẫn local dạng `/uploads/...` để tương thích trong thời gian chuyển đổi.

- QR cũ sẽ được tạo lại lên Cloudinary khi backend khởi động.
- Ảnh món ăn và avatar cũ không thể tự chuyển nếu file gốc không còn trên server. Cần tải ảnh lại từ giao diện quản trị/hồ sơ.

## Kiểm tra nhanh

Sau khi Render ở trạng thái `Live`:

1. Upload ảnh món ăn mới và kiểm tra URL bắt đầu bằng `https://res.cloudinary.com/`.
2. Đổi avatar và kiểm tra URL mới trong phản hồi API.
3. Tạo lại QR của một bàn và kiểm tra `anhQr` là URL Cloudinary.
4. Vào Cloudinary Media Library để kiểm tra ba thư mục `lumora/foods`, `lumora/avatars`, `lumora/qrcodes`.
