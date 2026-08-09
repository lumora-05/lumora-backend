# API đặt món giao tận nơi

## Luồng nghiệp vụ hiện tại

1. Khách nhập thông tin người nhận và địa chỉ; backend kiểm tra định dạng số điện thoại, xác định khu vực/phí giao.
2. Khách gửi đơn ở trạng thái `CHO_XAC_NHAN`.
3. Thu ngân/admin xác nhận sau khi backend kiểm tra khả năng đáp ứng nguyên liệu.
4. COD chuyển xuống bếp ngay. VietQR chuyển sang `CHO_THANH_TOAN`; khách chỉ được tạo QR sau bước này. Khi thu ngân xác nhận tiền, backend kiểm tra lại nguyên liệu rồi mới chuyển bếp.
5. Khi tiến độ bếp đạt ngưỡng cấu hình (mặc định 70%), backend có thể điều phối sớm GrabExpress (Demo). Chỉ khi toàn bộ món hoàn tất mới cho bàn giao.
6. Sau bàn giao, kết quả giao được nhận từ webhook đối tác. Với bản demo có API mô phỏng webhook dành cho CASHIER/ADMIN.
7. Đối tác báo giao thành công -> `CHO_DOI_SOAT`; thu ngân đối soát và tạo hóa đơn -> `HOAN_THANH`.
8. Nếu tiền VietQR đã thu nhưng tổng tiền giảm hoặc đơn bị hủy, hệ thống chuyển `CHO_HOAN_TIEN`; thu ngân phải ghi nhận giao dịch hoàn tiền. Hoàn hết đơn bị hủy -> `DA_HOAN_TIEN`.
9. VietQR không thanh toán trong thời hạn cấu hình (mặc định 15 phút) tự hủy; đơn `CHO_XAC_NHAN` quá lâu được cảnh báo cho nhân viên.

Không thêm vai trò `SHIPPER`; `DeliveryProviderService` vẫn là cổng tích hợp vận chuyển.

## Trạng thái giao hàng

| Trạng thái | Ý nghĩa |
|---|---|
| `CHO_XAC_NHAN` | Khách vừa gửi đơn |
| `CHO_THANH_TOAN` | Nhà hàng đã nhận đơn VietQR, đang chờ khách thanh toán |
| `DANG_CHUAN_BI` | Đơn đã xuống bếp |
| `CHO_TAI_XE_NHAN` | Toàn bộ món sẵn sàng, đã có tài xế/mã vận đơn |
| `DANG_GIAO` | Đã bàn giao cho tài xế |
| `CHO_DOI_SOAT` | Đối tác báo giao thành công, chờ thu ngân đối soát |
| `HOAN_THANH` | Đã đối soát và tạo hóa đơn |
| `GIAO_THAT_BAI` | Đối tác báo giao thất bại |
| `DA_HUY` | Đơn bị hủy/từ chối/hết hạn |

Trạng thái thanh toán gồm `CHO_THANH_TOAN`, `DA_THANH_TOAN`, `CHO_HOAN_TIEN`, `DA_HOAN_TIEN`, `HET_HAN`, `DA_HUY`.

## API công khai

### Tính phí do backend quyết định

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

Frontend không gửi `phiGiaoHang` hoặc `khuVucGiaoHang` khi tạo đơn.

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
  "phuongThucThanhToan": "VIETQR",
  "items": [{ "maMonAn": 5, "soLuong": 2, "ghiChu": "Không cay" }]
}
```

### Tra cứu, VietQR và hủy

```http
GET  /api/customer/delivery/orders/{trackingToken}
GET  /api/customer/delivery/orders/{trackingToken}/vietqr
POST /api/customer/delivery/orders/{trackingToken}/cancel
```

VietQR chỉ tạo khi đơn ở `CHO_THANH_TOAN`. Khách được tự hủy khi `CHO_XAC_NHAN`, hoặc khi `CHO_THANH_TOAN` nhưng chưa ghi nhận tiền.

## API CASHIER/ADMIN

```http
POST /api/delivery-orders/{orderId}/confirm
POST /api/delivery-orders/{orderId}/reject
POST /api/delivery-orders/{orderId}/confirm-vietqr
POST /api/delivery-orders/{orderId}/confirm-refund
POST /api/delivery-orders/{orderId}/handover
POST /api/delivery-orders/{orderId}/complete
POST /api/delivery-orders/{orderId}/retry
```

`confirm` không yêu cầu VietQR đã trả trước. Với VietQR, nó chỉ nhận đơn và mở bước thanh toán. `confirm-vietqr` mới chuyển đơn xuống bếp.

Admin có thể dùng API hủy món hiện có `POST /api/orders/items/{itemId}/cancel` cho đơn giao hàng trước khi bàn giao. Nếu VietQR đã thu tiền, backend tự tính khoản chênh lệch cần hoàn.

## Webhook vận chuyển

Webhook thật/mô phỏng từ đối tác:

```http
POST /api/delivery-provider/webhook
X-Delivery-Webhook-Token: <DELIVERY_PROVIDER_WEBHOOK_TOKEN>
```

```json
{
  "maVanDon": "GRAB-DEMO-...",
  "trangThai": "GIAO_THANH_CONG",
  "lyDo": null,
  "eventId": "provider-event-001"
}
```

Endpoint public chỉ chấp nhận token cấu hình bằng biến môi trường. Mặc định token để trống nên webhook ngoài bị khóa. Trong giao diện demo, CASHIER/ADMIN có thể dùng:

```http
POST /api/delivery-orders/{orderId}/simulate-provider-result
```

để mô phỏng cùng luồng webhook mà không cần vai trò shipper.

## Cấu hình

Các biến chính:

```text
DELIVERY_INNER_AREA_FEE=15000
DELIVERY_NEARBY_AREA_FEE=25000
DELIVERY_SUPPORTED_CITY=Đà Nẵng
DELIVERY_INNER_DISTRICTS=Thanh Khê,Hải Châu
DELIVERY_NEARBY_DISTRICTS=Sơn Trà,Ngũ Hành Sơn,Cẩm Lệ,Liên Chiểu,Hòa Vang
DELIVERY_PAYMENT_TIMEOUT_MINUTES=15
DELIVERY_CONFIRMATION_WARNING_MINUTES=10
DELIVERY_DRIVER_ASSIGNMENT_PROGRESS_PERCENT=70
DELIVERY_PROVIDER_WEBHOOK_TOKEN=<secret>
```

Cơ sở dữ liệu dùng `spring.jpa.hibernate.ddl-auto=update`; file `database/delivery_order_upgrade.sql` cũng đã được cập nhật để có thể nâng cấp PostgreSQL/Neon chủ động.

## Google Maps / tính phí theo quãng đường

Luồng giao hàng hỗ trợ Google Places + Routes API. Khi frontend gửi `googlePlaceId`, backend gọi Routes API để tính lại quãng đường từ LUMORA tới khách và tự quyết định phí; frontend không được gửi số tiền phí giao hàng.

Cấu hình chi tiết xem `GOOGLE_MAPS_DELIVERY_SETUP.md`. Nếu chưa có `GOOGLE_MAPS_SERVER_API_KEY`, backend giữ cơ chế quận/huyện cũ làm fallback để không làm gián đoạn demo.
