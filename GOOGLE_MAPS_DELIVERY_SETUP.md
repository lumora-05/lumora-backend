# Google Maps cho đặt món giao tận nơi

## Luồng mới

1. Frontend dùng **Maps JavaScript API + Places API (New)** để gợi ý địa chỉ.
2. Khách bắt buộc chọn một kết quả Google Maps khi `VITE_GOOGLE_MAPS_API_KEY` được cấu hình.
3. Frontend gửi `googlePlaceId` và địa chỉ đã chuẩn hóa cho `POST /api/customer/delivery/quote`.
4. Backend dùng **Routes API - Compute Routes** từ địa chỉ nhà hàng đến `googlePlaceId`.
5. Backend lấy `distanceMeters`, `duration` và `encodedPolyline`, sau đó tự tính phí:
   - đến 3 km: 15.000đ
   - trên 3 đến 6 km: 20.000đ
   - trên 6 đến 10 km: 30.000đ
   - trên 10 km: từ chối giao hàng
6. Frontend chỉ hiển thị kết quả backend; không tự tính phí.
7. Khi tạo đơn, backend tính tuyến đường lại để không tin số tiền/quãng đường do client gửi lên.

Nếu chưa cấu hình Google Maps, hệ thống tự quay về bảng phí quận/huyện cũ để không làm gián đoạn chức năng.

## Biến môi trường backend

```properties
GOOGLE_MAPS_ENABLED=true
GOOGLE_MAPS_SERVER_API_KEY=<server-key>
GOOGLE_MAPS_ORIGIN_ADDRESS=139 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng, Việt Nam
GOOGLE_MAPS_TIER1_DISTANCE_KM=3
GOOGLE_MAPS_TIER2_DISTANCE_KM=6
GOOGLE_MAPS_MAX_DELIVERY_DISTANCE_KM=10
GOOGLE_MAPS_TIER1_FEE=15000
GOOGLE_MAPS_TIER2_FEE=20000
GOOGLE_MAPS_TIER3_FEE=30000
```

Key backend chỉ nên bật **Routes API** và giới hạn theo IP khi môi trường hosting cho phép.

## Biến môi trường frontend

```env
VITE_GOOGLE_MAPS_API_KEY=<browser-key>
```

Key frontend chỉ nên bật **Maps JavaScript API** và **Places API (New)**, đồng thời giới hạn HTTP referrer cho domain triển khai (ví dụ `https://lumora.food/*` và domain Cloudflare Pages dùng để kiểm thử).

## API quote

Ví dụ payload Google Maps:

```json
{
  "tinhThanh": "Đà Nẵng",
  "quanHuyen": "Thanh Khê",
  "phuongXa": "Chính Gián",
  "diaChiChiTiet": "...",
  "googlePlaceId": "...",
  "googleFormattedAddress": "..."
}
```

Các trường Google trả về thêm:

- `googleMaps`: có thực sự tính bằng Routes API hay đang fallback.
- `quangDuongMet`: quãng đường lái xe.
- `thoiGianDuKienGiay`: thời gian di chuyển ước tính.
- `encodedPolyline`: tuyến đường dùng để vẽ trên bản đồ frontend.
