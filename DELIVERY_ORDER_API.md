# API đặt món giao tận nơi

## Phạm vi nghiệp vụ

- Khách đặt trực tiếp trên website LUMORA.
- Không thêm vai trò `SHIPPER`.
- Thu ngân hoặc admin xác nhận đơn và cập nhật kết quả giao; không nhập thủ công tài xế.
- Bếp vẫn xử lý từng suất món riêng.
- Khi toàn bộ suất món hợp lệ hoàn thành, backend gọi dịch vụ vận chuyển mô phỏng để nhận mã vận đơn và thông tin tài xế.
- Đơn giao hàng không chiếm bàn và không làm thay đổi trạng thái bàn ăn.

## Trạng thái giao hàng

| Trạng thái | Ý nghĩa |
|---|---|
| `CHO_XAC_NHAN` | Khách vừa gửi đơn, nhà hàng chưa xác nhận |
| `DANG_CHUAN_BI` | Đơn đã xác nhận và đang được bếp chế biến |
| `CHO_TAI_XE_NHAN` | Tất cả món hoàn thành; dịch vụ vận chuyển đã cấp mã vận đơn và điều phối tài xế |
| `DANG_GIAO` | Thu ngân đã bàn giao món cho tài xế được đơn vị vận chuyển điều phối |
| `HOAN_THANH` | Giao thành công và đã tạo hóa đơn |
| `GIAO_THAT_BAI` | Lần giao hiện tại không thành công |
| `DA_HUY` | Khách hủy hoặc nhà hàng từ chối trước khi xác nhận |

## Phí giao hàng mặc định

- `NOI_THANH`: 15.000 đồng.
- `LAN_CAN`: 25.000 đồng.

Có thể thay đổi bằng biến môi trường:

```text
DELIVERY_INNER_AREA_FEE
DELIVERY_NEARBY_AREA_FEE
DELIVERY_SHIPPING_CODE_PREFIX (giữ để tương thích cấu hình cũ, không còn dùng để sinh mã mới)
DELIVERY_MOCK_PROVIDER_NAME
DELIVERY_MOCK_WAYBILL_PREFIX
DELIVERY_MAX_UNITS_PER_ITEM
DELIVERY_MAX_UNITS_PER_ORDER
```

## API công khai cho khách

### Tạo đơn

```http
POST /api/customer/delivery/orders
```

```json
{
  "clientRequestId": "2acb522d-5ce8-4722-bb97-97ca4dd8ec61",
  "tenNguoiNhan": "Nguyễn Văn A",
  "soDienThoaiNhan": "0912345678",
  "diaChiGiaoHang": "123 Nguyễn Văn Linh, Đà Nẵng",
  "khuVucGiaoHang": "NOI_THANH",
  "ghiChuGiaoHang": "Gọi trước khi đến",
  "phuongThucThanhToan": "COD",
  "ghiChuDonHang": "Giao sau 18 giờ",
  "items": [
    {
      "maMonAn": 5,
      "soLuong": 2,
      "ghiChu": "Không cay"
    }
  ]
}
```

`clientRequestId` phải do frontend tạo một lần cho mỗi lần đặt hàng. Gửi lại cùng mã và cùng số điện thoại sẽ trả về đơn đã tạo, tránh nhân đôi khi khách bấm nhiều lần.

### Tra cứu đơn

```http
GET /api/customer/delivery/orders/{trackingToken}
```

### Lấy VietQR

```http
GET /api/customer/delivery/orders/{trackingToken}/vietqr
```

Chỉ dùng khi đơn chọn `VIETQR`. API này không tự xác nhận đã nhận tiền.

### Khách hủy đơn

```http
POST /api/customer/delivery/orders/{trackingToken}/cancel
```

```json
{
  "lyDo": "Tôi nhập sai địa chỉ"
}
```

Khách chỉ được hủy khi đơn còn `CHO_XAC_NHAN`.

## API dành cho thu ngân/admin

### Danh sách và chi tiết

```http
GET /api/delivery-orders
GET /api/delivery-orders?deliveryStatus=CHO_XAC_NHAN
GET /api/delivery-orders/{orderId}
```

### Xác nhận VietQR

```http
POST /api/delivery-orders/{orderId}/confirm-vietqr
```

```json
{
  "maGiaoDich": "MB202608060001",
  "ghiChu": "Đã kiểm tra tài khoản"
}
```

### Xác nhận hoặc từ chối đơn

```http
POST /api/delivery-orders/{orderId}/confirm
POST /api/delivery-orders/{orderId}/reject
```

Khi từ chối:

```json
{
  "lyDo": "Ngoài khu vực giao hàng"
}
```

Đơn VietQR phải được thu ngân xác nhận đã nhận tiền trước khi xác nhận chuyển xuống bếp.

### Bàn giao cho tài xế đã được điều phối

Khi bếp hoàn thành toàn bộ món, backend tự gọi `DeliveryProviderService`. Bản demo dùng
`MockDeliveryProviderService` để mô phỏng API đối tác và tự nhận:

- mã vận đơn đối tác, ví dụ `GRAB-DEMO-00000125-ABC123DEF4`;
- tên đơn vị vận chuyển;
- tên tài xế;
- số điện thoại tài xế.

Thu ngân không nhập các thông tin này. Khi tài xế đến nhận món, thu ngân chỉ xác nhận bàn giao:

```http
POST /api/delivery-orders/{orderId}/handover
```

```json
{
  "ghiChuBanGiao": "Đã bàn giao đủ 3 túi"
}
```

Các trường `donViVanChuyen`, `tenNguoiGiao`, `soDienThoaiNguoiGiao` trong request cũ vẫn được backend chấp nhận để tương thích frontend cũ nhưng bị bỏ qua, không thể ghi đè thông tin tài xế do đối tác điều phối.

Chỉ thực hiện được khi trạng thái là `CHO_TAI_XE_NHAN` và đơn đã có đầy đủ thông tin điều phối.

### Giao thành công, thất bại và giao lại

```http
POST /api/delivery-orders/{orderId}/complete
POST /api/delivery-orders/{orderId}/fail
POST /api/delivery-orders/{orderId}/retry
```

Khi thất bại:

```json
{
  "lyDo": "Không liên lạc được với người nhận"
}
```

- `complete` tạo hóa đơn và chuyển đơn hàng sang `DA_THANH_TOAN`.
- Với COD, tiền được ghi nhận khi giao thành công.
- Với VietQR, hệ thống yêu cầu trạng thái thanh toán đã được thu ngân xác nhận.
- `retry` tự gửi lại yêu cầu đến dịch vụ vận chuyển mô phỏng, nhận mã vận đơn/tài xế mới và đưa đơn về `CHO_TAI_XE_NHAN`.

## Dịch vụ vận chuyển mô phỏng

- Không thêm vai trò `SHIPPER`.
- Mô phỏng GrabExpress; không gọi API GrabExpress/Grab thật.
- `DeliveryProviderService` là cổng tích hợp; khi có API thật chỉ thay implementation.
- Bản demo hiện dùng `MockDeliveryProviderService` với tên hiển thị `GrabExpress (Demo)` và mã vận đơn dạng `GRAB-DEMO-...`.
- Không cần thêm cột database so với bản giao hàng trước vì tái sử dụng `ma_van_chuyen`, `don_vi_van_chuyen`, `ten_nguoi_giao`, `so_dien_thoai_nguoi_giao`.

## Nâng cấp cơ sở dữ liệu

Chạy file:

```text
database/delivery_order_upgrade.sql
```

Nếu đang dùng `spring.jpa.hibernate.ddl-auto=update`, Hibernate có thể tạo cột/bảng, nhưng vẫn nên chạy SQL trên môi trường production để bảo đảm đầy đủ chỉ mục và ràng buộc.
