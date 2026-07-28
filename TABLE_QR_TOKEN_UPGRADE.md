# Nâng cấp QR token riêng cho từng bàn

## Thay đổi backend

- Bảng `ban_an` có thêm cột `qr_token` duy nhất.
- Token được tạo ngẫu nhiên khi thêm bàn và giữ nguyên khi tạo lại ảnh QR.
- `ma_qr` dạng `QR0001` vẫn được giữ để frontend admin hiển thị.
- Nội dung ảnh QR đổi từ `/table/{maBan}` thành `/table/{qrToken}`.
- Dữ liệu bàn cũ được tự bổ sung token khi ứng dụng khởi động; ảnh QR cũ được tạo lại để chứa token.

## API khách hàng mới

- `GET /api/customer/qr/{qrToken}`
- `GET /api/customer/qr/{qrToken}/menu`
- `GET /api/customer/qr/{qrToken}/orders/current`
- `GET /api/customer/qr/{qrToken}/orders`
- `POST /api/customer/orders`: gửi `qrToken` cùng danh sách món.

Ví dụ body đặt món:

```json
{
  "qrToken": "7f3a9c2e-81d4-4b7f-a29c-5d0e8a1f6b32",
  "ghiChu": "",
  "items": [
    { "maMonAn": 5, "soLuong": 2, "ghiChu": "Ít cay" }
  ]
}
```

Các endpoint dùng `maBan` cũ vẫn được giữ tạm thời để không ảnh hưởng các phần khác trong giai đoạn cập nhật frontend.
