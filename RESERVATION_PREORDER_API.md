# API đặt món trước gắn với lịch đặt bàn

## Mục tiêu nghiệp vụ

Chức năng này không phải đơn mang đi. Khách chỉ được chọn món trước sau khi lịch đặt bàn đã được nhà hàng xác nhận. Nhà hàng duyệt thực đơn trước, nhưng chỉ chuyển món xuống bếp sau khi khách đã check-in và được xếp bàn thực tế. Nhờ vậy hệ thống giảm thời gian gọi món tại bàn nhưng không chế biến cho khách không đến.

## Trạng thái đặt món trước

- `CHUA_DAT`: chưa chọn món.
- `CHO_XAC_NHAN`: khách đã gửi, nhà hàng cần kiểm tra.
- `DA_XAC_NHAN`: nhà hàng đã duyệt.
- `TU_CHOI`: cần khách chỉnh sửa thực đơn.
- `DA_CHUYEN_BEP`: đã tạo đơn hàng thật và chuyển món xuống bếp.
- `DA_HUY`: phần đặt món trước đã bị hủy.

## API khách hàng

Tất cả API dùng `maTraCuu` và số điện thoại của lịch đặt bàn.

### Xem thực đơn đặt trước

`GET /api/customer/reservations/{code}/preorder?phone={phone}`

### Tạo hoặc cập nhật thực đơn

`PUT /api/customer/reservations/{code}/preorder?phone={phone}`

```json
{
  "ghiChu": "Chuẩn bị ít cay",
  "items": [
    { "maMonAn": 1, "soLuong": 2, "ghiChu": "Không hành" },
    { "maMonAn": 5, "soLuong": 1, "ghiChu": null }
  ]
}
```

Điều kiện: lịch đặt bàn đang `DA_XAC_NHAN`, khách chưa check-in và lịch chưa phát sinh đơn hàng.

### Hủy phần đặt món trước

`POST /api/customer/reservations/{code}/preorder/cancel?phone={phone}`

```json
{ "reason": "Thay đổi kế hoạch" }
```

## API nhân viên phục vụ / quản trị viên

### Xem thực đơn đặt trước

`GET /api/reservations/{id}/preorder`

### Duyệt thực đơn

`POST /api/reservations/{id}/preorder/confirm`

```json
{
  "soPhutChuanBiTruoc": 30,
  "ghiChu": "Đã kiểm tra món và nguyên liệu"
}
```

`soPhutChuanBiTruoc` dùng để hiển thị thời điểm dự kiến chuẩn bị, từ 15 đến 180 phút. Hệ thống không tự động chuyển bếp khi khách chưa đến.

### Từ chối để khách điều chỉnh

`POST /api/reservations/{id}/preorder/reject`

```json
{ "reason": "Món sốt cá hiện hết nguyên liệu" }
```

### Chuyển xuống bếp

`POST /api/reservations/{id}/preorder/send-to-kitchen`

Điều kiện bắt buộc:

1. Thực đơn đã `DA_XAC_NHAN`.
2. Khách đã check-in.
3. Lịch đã được xếp bàn, trạng thái `DA_XEP_BAN`.
4. Bàn chưa có đơn hàng khác.

Khi thành công, backend tạo đơn hàng `DA_XAC_NHAN`, tạo các chi tiết món `CHO_BEP`, liên kết đơn với lịch đặt bàn và gửi WebSocket cho bếp, phục vụ và khách.

## Quy tắc chống tạo đơn trùng

Nếu lịch đã xếp bàn còn thực đơn đặt trước ở `CHO_XAC_NHAN` hoặc `DA_XAC_NHAN`, hệ thống không cho tạo một đơn thường mới tại bàn. Nhân viên phải xử lý phần món đặt trước trước.

## Đồng bộ khi lịch đặt bàn thay đổi

- Khách sửa lịch đã xác nhận: thực đơn phải được duyệt lại.
- Lịch bị hủy, từ chối hoặc khách không đến: thực đơn chưa chuyển bếp được chuyển sang `DA_HUY`.
- Sau khi đã chuyển bếp, các bước chế biến, phục vụ và thanh toán dùng chung luồng đơn hàng hiện có.
