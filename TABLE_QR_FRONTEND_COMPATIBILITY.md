# Kiểm tra tương thích Frontend Bàn & QR

## Các lỗi đã sửa

1. Frontend gửi `khuVuc`, `tenKhuVuc`, `sucChua` nhưng backend cũ bỏ qua.
2. Backend cũ tự tạo QR mỗi lần thêm hoặc sửa bàn, không đúng luồng hai tab.
3. QR cũ trỏ tới mã bàn trực tiếp; QR mới dùng `/table/{qrToken}` để không lộ `maBan`.
4. Backend cũ thiếu trạng thái QR và thời gian tạo/cập nhật.
5. Backend cũ dùng `maQr` để lưu đường dẫn thay vì mã hiển thị như `QR0001`.
6. CORS cũ chỉ cho cổng 5173 nên lỗi khi Vite chạy 5174.

## Hành vi mới

- Thêm bàn: tạo bàn với QR `CHUA_TAO`.
- Nút Tạo QR: sinh ảnh chứa `qrToken`, vẫn giữ mã hiển thị `QRxxxx`, trạng thái `DANG_HOAT_DONG` và timestamp.
- Tạo lại QR: sinh file mới và xóa ảnh cũ.
- Tạm ngưng/ngừng sử dụng: cập nhật qua endpoint trạng thái QR.
- Khách chỉ truy cập menu khi QR đang hoạt động.
- File QR được phục vụ công khai tại `/uploads/qrcodes/...`.
- Mỗi bàn có một `qrToken` duy nhất; tạo lại ảnh QR không đổi token.
