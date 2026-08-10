# Bản đồ giao hàng miễn phí (OpenStreetMap + openrouteservice)

Luồng giao hàng không còn dùng Google Maps Platform hoặc Google Cloud Billing.

## Thành phần

- Frontend: MapLibre GL JS + raster tiles OpenStreetMap.
- Backend: openrouteservice/HeiGIT để geocode địa chỉ và tính quãng đường lái xe.
- API key openrouteservice chỉ nằm ở backend, không gửi xuống trình duyệt.

## Cấu hình bắt buộc trên Render

Tạo tài khoản openrouteservice/HeiGIT và tạo API key Standard 0€, sau đó thêm biến môi trường:

```text
OPENROUTESERVICE_API_KEY=<api-key-cua-ban>
```

Không cần `VITE_GOOGLE_MAPS_API_KEY`, `GOOGLE_MAPS_SERVER_API_KEY` hay Google Cloud Billing cho luồng giao hàng nữa.

## Cấu hình tùy chọn

```text
OPENROUTESERVICE_ENABLED=true
OPENROUTESERVICE_ORIGIN_ADDRESS=139 Nguyễn Thị Thập, Thanh Khê, Đà Nẵng, Việt Nam
OPENROUTESERVICE_TIER1_DISTANCE_KM=3
OPENROUTESERVICE_TIER2_DISTANCE_KM=6
OPENROUTESERVICE_MAX_DELIVERY_DISTANCE_KM=10
OPENROUTESERVICE_TIER1_FEE=15000
OPENROUTESERVICE_TIER2_FEE=20000
OPENROUTESERVICE_TIER3_FEE=30000
```

Nếu chưa có `OPENROUTESERVICE_API_KEY`, backend vẫn giữ bảng phí theo quận/huyện dự phòng cũ.

## Attribution

Bản đồ frontend hiển thị attribution `© OpenStreetMap contributors` theo yêu cầu của OpenStreetMap.
