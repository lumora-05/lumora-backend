# API đặt món giao tận nơi

## Luồng nghiệp vụ hiện tại

1. Khách chọn địa chỉ giao hàng trước khi xem thực đơn. Backend kiểm tra nhà hàng đang trong giờ nhận đơn và địa chỉ thuộc phạm vi giao.
2. Backend tính phí giao hàng và thời gian nhận dự kiến. Nếu có openrouteservice, ETA gồm thời gian chuẩn bị dự kiến + thời gian di chuyển; nếu chưa cấu hình dịch vụ bản đồ thì dùng thời gian giao dự phòng.
3. Khách chọn món, nhập thông tin nhận hàng, ghi chú, mã khuyến mãi (nếu có) và phương thức thanh toán.
4. Khi khách xác nhận, backend kiểm tra lần cuối: giờ nhận đơn, địa chỉ/phí giao, món còn bán, nguyên liệu đủ, giá từ database và khuyến mãi còn hợp lệ.
5. COD hợp lệ được tạo và chuyển thẳng xuống bếp, không còn bước thu ngân duyệt đơn.
6. VietQR hợp lệ được tạo ở `CHO_THANH_TOAN`; sau khi thanh toán được ghi nhận, backend kiểm tra lại nguyên liệu rồi chuyển thẳng xuống bếp.
7. Khi bếp đã bắt đầu và gần đến thời điểm món dự kiến sẵn sàng, backend tự điều phối đối tác vận chuyển. Không còn dùng ngưỡng phần trăm số món hoàn thành.
8. Chỉ khi toàn bộ món hoàn tất, đơn mới ở trạng thái sẵn sàng để bàn giao cho tài xế.
9. Sau bàn giao, trạng thái giao hàng được cập nhật từ webhook đối tác. Bản demo có API mô phỏng webhook cho CASHIER/ADMIN.
10. Đối tác báo giao thành công thì khách nhìn thấy ngay `HOAN_THANH`. Trạng thái `CHO_DOI_SOAT` chỉ còn là trạng thái nội bộ tương thích dữ liệu/kế toán và không xuất hiện trong hành trình khách.
11. VietQR không thanh toán trong thời hạn cấu hình sẽ tự hủy. Nếu đơn đã thu tiền nhưng sau đó phải hủy/giảm tiền, hệ thống ghi nhận khoản cần hoàn như nghiệp vụ nội bộ.
12. Sau khi giao thành công, khách có thể gửi đánh giá bằng mô-đun đánh giá hiện có hoặc liên hệ nhà hàng để được hỗ trợ.

Không thêm vai trò `SHIPPER`; `DeliveryProviderService` là cổng tích hợp/mô phỏng đối tác vận chuyển.

## Trạng thái khách hàng nhìn thấy

| Trạng thái | Ý nghĩa |
|---|---|
| `CHO_THANH_TOAN` | Chờ thanh toán VietQR |
| `DANG_CHUAN_BI` | Đơn đã được chuyển xuống bếp |
| `CHO_TAI_XE_NHAN` | Món đã sẵn sàng, chờ tài xế nhận |
| `DANG_GIAO` | Tài xế đã nhận món và đang giao |
| `HOAN_THANH` | Đã giao thành công |
| `GIAO_THAT_BAI` | Giao không thành công |
| `DA_HUY` | Đơn đã hủy |

`CHO_DOI_SOAT` có thể còn tồn tại nội bộ để tương thích kế toán/dữ liệu cũ nhưng API tracking công khai ánh xạ thành `HOAN_THANH`.

Trạng thái thanh toán gồm `CHO_THANH_TOAN`, `DA_THANH_TOAN`, `CHO_HOAN_TIEN`, `DA_HOAN_TIEN`, `HET_HAN`, `DA_HUY`.

## API công khai

### Kiểm tra địa chỉ, giờ nhận đơn, phí và ETA

```http
POST /api/customer/delivery/quote
```

```json
{
  "tinhThanh": "Đà Nẵng",
  "quanHuyen": "Thanh Khê",
  "phuongXa": "Chính Gián",
  "diaChiChiTiet": "123 Nguyễn Văn Linh"
}
```

Frontend không tự quyết định `phiGiaoHang` hoặc `khuVucGiaoHang`. Nếu nhà hàng ngoài giờ nhận đơn hoặc địa chỉ ngoài phạm vi giao, backend từ chối ngay tại bước này.

### Tạo đơn

```http
POST /api/customer/delivery/orders
```

```json
{
  "clientRequestId": "2acb522d-5ce8-4722-bb97-97ca4dd8ec61",
  "tenNguoiNhan": "Nguyễn Văn A",
  "soDienThoaiNhan": "0912345678",
  "diaChiChiTiet": "123 Nguyễn Văn Linh",
  "phuongXa": "Chính Gián",
  "quanHuyen": "Thanh Khê",
  "tinhThanh": "Đà Nẵng",
  "ghiChuGiaoHang": "Gọi trước khi đến",
  "maCodeKhuyenMai": "LUMORA10",
  "phuongThucThanhToan": "VIETQR",
  "items": [{ "maMonAn": 5, "soLuong": 2, "ghiChu": "Không cay" }]
}
```

Backend luôn đọc giá món từ database, kiểm tra nguyên liệu và khóa/kiểm tra mã khuyến mãi tại thời điểm tạo đơn. Frontend chỉ hiển thị số tiền dự kiến.

### Tra cứu, VietQR và hủy

```http
GET  /api/customer/delivery/orders/{trackingToken}
GET  /api/customer/delivery/orders/{trackingToken}/vietqr
POST /api/customer/delivery/orders/{trackingToken}/cancel
```

VietQR chỉ tạo khi đơn ở `CHO_THANH_TOAN`. Khách chỉ tự hủy đơn VietQR khi chưa thanh toán; đơn COD hợp lệ được chuyển thẳng xuống bếp nên cần liên hệ nhà hàng nếu có sự cố.

## API CASHIER/ADMIN

Không còn API `confirm` hoặc `reject` đơn giao hàng trước khi xuống bếp.

```http
POST /api/delivery-orders/{orderId}/confirm-vietqr
POST /api/delivery-orders/{orderId}/confirm-refund
POST /api/delivery-orders/{orderId}/handover
POST /api/delivery-orders/{orderId}/complete
POST /api/delivery-orders/{orderId}/retry
POST /api/delivery-orders/{orderId}/fail
POST /api/delivery-orders/{orderId}/simulate-provider-result
```

`confirm-vietqr` là bước ghi nhận thanh toán trong bản demo; sau khi ghi nhận thành công, đơn được chuyển xuống bếp ngay. `complete` là bước hóa đơn/kế toán nội bộ sau khi đã giao thành công, không phải một trạng thái khách phải chờ.

Admin có thể dùng API hủy món hiện có cho đơn giao hàng trước khi bàn giao. Nếu VietQR đã thu tiền, backend tự tính khoản chênh lệch cần hoàn.

## Webhook vận chuyển

Webhook thật/mô phỏng từ đối tác:

```http
POST /api/delivery-provider/webhook
X-Delivery-Webhook-Token: <DELIVERY_PROVIDER_WEBHOOK_TOKEN>
```

```json
{
  "maVanDon": "DELIVERY-DEMO-...",
  "trangThai": "GIAO_THANH_CONG",
  "lyDo": null,
  "eventId": "provider-event-001"
}
```

Endpoint public chỉ chấp nhận token cấu hình. Trong giao diện demo, CASHIER/ADMIN có thể dùng `simulate-provider-result` để mô phỏng cùng luồng webhook mà không cần vai trò shipper.

## Cấu hình

Các biến chính:

```text
DELIVERY_INNER_AREA_FEE=15000
DELIVERY_NEARBY_AREA_FEE=25000
DELIVERY_SUPPORTED_CITY=Đà Nẵng
DELIVERY_INNER_DISTRICTS=Thanh Khê,Hải Châu
DELIVERY_NEARBY_DISTRICTS=Sơn Trà,Ngũ Hành Sơn,Cẩm Lệ,Liên Chiểu,Hòa Vang
DELIVERY_PAYMENT_TIMEOUT_MINUTES=15
DELIVERY_PREPARATION_MINUTES=25
DELIVERY_DRIVER_ASSIGNMENT_LEAD_MINUTES=8
DELIVERY_FALLBACK_DELIVERY_MINUTES=20
DELIVERY_PROVIDER_WEBHOOK_TOKEN=<secret>
```

- `DELIVERY_PREPARATION_MINUTES`: thời gian chuẩn bị món dự kiến dùng để tính ETA.
- `DELIVERY_DRIVER_ASSIGNMENT_LEAD_MINUTES`: số phút trước thời điểm dự kiến món sẵn sàng để bắt đầu điều phối tài xế.
- `DELIVERY_FALLBACK_DELIVERY_MINUTES`: thời gian giao dự phòng khi chưa có Routes API.

Cơ sở dữ liệu dùng `spring.jpa.hibernate.ddl-auto=update`; file `database/delivery_order_upgrade.sql` cũng được giữ để nâng cấp PostgreSQL/Neon chủ động.

## OpenStreetMap + openrouteservice / tính phí theo quãng đường

Luồng giao hàng hỗ trợ Google Places + Routes API. Khi frontend gửi `googlePlaceId`, backend gọi Routes API để tính lại quãng đường từ LUMORA tới khách và tự quyết định phí; frontend không được gửi số tiền phí giao hàng.

Cấu hình chi tiết xem `OPEN_MAP_DELIVERY_SETUP.md`. Nếu chưa có `OPENROUTESERVICE_API_KEY`, backend giữ cơ chế quận/huyện cũ làm fallback để không làm gián đoạn demo.
