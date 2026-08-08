# System Settings API

Chức năng Cài đặt hệ thống dành cho Admin. Cấu hình được lưu trong PostgreSQL; logo và banner được upload lên Cloudinary và database chỉ lưu URL.

Trang Cài đặt hệ thống được chia thành 6 nhóm:

1. Thông tin nhà hàng
2. Thương hiệu & giao diện
3. Đặt bàn
4. Thanh toán
5. Tích điểm
6. Chatbot

## Public

### GET `/api/system-settings/public`
Không cần đăng nhập. Chỉ trả về dữ liệu an toàn cần cho website công khai:
`restaurantName`, `address`, `phone`, `email`, `openingHours`, `reservationUrl`, `menuUrl`, `logoUrl`, `bannerUrl`.

Các cấu hình thanh toán, tích điểm và chatbot không được trả qua endpoint public.

## Admin

Tất cả endpoint dưới đây yêu cầu JWT có role `ADMIN`.

### GET `/api/system-settings`
Lấy toàn bộ cấu hình hiện tại của 6 nhóm.

### PUT `/api/system-settings`
JSON mẫu đầy đủ:

```json
{
  "restaurantName": "LUMORA",
  "address": "139 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng",
  "phone": "0900000000",
  "email": "contact@lumora.food",
  "openingHours": "07:00 - 22:00",
  "reservationUrl": "/reservations",
  "menuUrl": "/#menu",

  "reservationDefaultDurationMinutes": 120,
  "reservationPreparationMinutes": 30,
  "reservationNoShowGraceMinutes": 15,

  "vietQrBankId": "970422",
  "vietQrBankName": "MB Bank",
  "vietQrAccountNo": "0123456789",
  "vietQrAccountName": "LUMORA RESTAURANT",
  "vietQrTemplate": "compact2",
  "vietQrDescriptionPrefix": "LUMORA",

  "loyaltyMoneyPerEarnedPoint": 10000,
  "loyaltyValuePerRedeemedPoint": 1000,
  "loyaltyMinimumRedeemPoints": 20,
  "loyaltyMaximumRedeemRatio": 0.20,

  "chatbotEnabled": true,
  "chatbotModel": "gpt-5-mini",
  "chatbotTimeoutSeconds": 20,
  "chatbotMaxOutputTokens": 700,
  "chatbotMaxHistoryMessages": 8,
  "chatbotMinimumConfidence": 0.45
}
```

Các trường cấu hình mới là nullable ở request để frontend cũ vẫn tương thích. Khi frontend mới gửi giá trị, backend cập nhật và đồng bộ vào runtime ngay lập tức.

### POST `/api/system-settings/logo`
`multipart/form-data`, field `file`. Tối đa 5 MB; hỗ trợ jpg/jpeg/png/webp/gif.

### POST `/api/system-settings/banner`
`multipart/form-data`, field `file`. Tối đa 5 MB; hỗ trợ jpg/jpeg/png/webp/gif.

### DELETE `/api/system-settings/logo`
Xóa logo tùy chỉnh hiện tại và dọn ảnh Cloudinary nếu có.

### DELETE `/api/system-settings/banner`
Xóa banner tùy chỉnh hiện tại và dọn ảnh Cloudinary nếu có.

## Tác động runtime

- **Thông tin nhà hàng:** đồng bộ vào `RestaurantInfoProperties`, chatbot tiếp tục dùng dữ liệu mới.
- **Đặt bàn:** thời lượng mặc định, thời gian chuẩn bị bàn và thời gian chờ khách trễ được `ReservationService` dùng trực tiếp.
- **Thanh toán:** thông tin VietQR đồng bộ vào `VietQrProperties`, `PaymentService` dùng ngay khi tạo QR.
- **Tích điểm:** chính sách tích/đổi điểm đồng bộ vào `LoyaltyPolicyProperties`, `LoyaltyService` tính theo cấu hình mới.
- **Chatbot:** bật/tắt, model, timeout, giới hạn token, lịch sử và ngưỡng tin cậy đồng bộ vào `ChatbotAiProperties`. API key và base URL vẫn lấy từ biến môi trường, không lưu trong database.

## Tương thích dữ liệu cũ

Nếu bảng `cai_dat_he_thong` đã có bản ghi từ phiên bản trước, các cột mới sẽ được tự điền bằng đúng giá trị mặc định/cấu hình cũ khi backend khởi động. Vì vậy không làm mất logo, banner hoặc thông tin nhà hàng đã lưu trước đó.
