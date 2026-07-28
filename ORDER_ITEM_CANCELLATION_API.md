# API hủy món và chọn lý do

## Quy tắc nghiệp vụ

- Món `CHO_BEP`: phục vụ hoặc admin hủy trực tiếp, bắt buộc chọn lý do.
- Món `DANG_NAU`/`DANG_CHE_BIEN`:
  - Phục vụ gửi yêu cầu; món tạm chuyển `YEU_CAU_HUY` và chờ admin duyệt.
  - Admin có thể hủy trực tiếp hoặc duyệt yêu cầu.
- Món `HOAN_THANH`, `DA_HOAN_THANH`, `DA_PHUC_VU`: không hủy theo luồng thông thường.
- Món đã hủy được giữ trong đơn với trạng thái `DA_HUY`, không bị xóa database và không tính vào tổng tiền/hóa đơn/thống kê món bán.
- Nếu toàn bộ món trong đơn đều bị hủy, đơn tự chuyển `DA_HUY` và bàn được giải phóng theo luồng hiện có.
- Đơn có yêu cầu hủy đang chờ không được phục vụ xác nhận để chuyển vào bếp.

## Mã lý do

- `KHACH_DOI_Y`
- `KHACH_GOI_NHAM`
- `NHAN_VIEN_NHAP_NHAM`
- `KHACH_CHO_QUA_LAU`
- `KHACH_DOI_MON`
- `HET_NGUYEN_LIEU`
- `BEP_KHONG_THE_CHE_BIEN`
- `MON_KHONG_DUNG_YEU_CAU`
- `LY_DO_KHAC` — bắt buộc nhập `ghiChu`

## 1. Phục vụ/admin hủy hoặc gửi yêu cầu hủy

`POST /api/orders/items/{itemId}/cancel`

Role: `WAITER`, `ADMIN`

```json
{
  "maLyDo": "KHACH_GOI_NHAM",
  "ghiChu": "Khách gọi nhầm số lượng"
}
```

- Trả `trangThaiMon=DA_HUY`, `trangThaiHuy=DA_DUYET` nếu hủy ngay.
- Trả `trangThaiMon=YEU_CAU_HUY`, `trangThaiHuy=CHO_DUYET` nếu cần admin duyệt.

## 2. Khách gửi yêu cầu hủy từ QR bàn

`POST /api/customer/qr/{qrToken}/orders/{orderId}/items/{itemId}/cancel-request`

Chỉ cho món `CHO_BEP` thuộc đúng đơn và đúng bàn của QR.

```json
{
  "maLyDo": "KHACH_DOI_Y",
  "ghiChu": "Muốn đổi sang món khác"
}
```

## 3. Danh sách yêu cầu hủy

`GET /api/orders/items/cancel-requests?status=CHO_DUYET`

Role: `WAITER`, `ADMIN`

Trạng thái hợp lệ: `CHO_DUYET`, `DA_DUYET`, `TU_CHOI`.
Phục vụ chỉ nhận yêu cầu thuộc khu vực được phân công.

## 4. Duyệt yêu cầu

`PUT /api/orders/items/{itemId}/cancel-request/approve`

Role: `WAITER`, `ADMIN`

```json
{
  "ghiChu": "Đã xác nhận với khách"
}
```

Phục vụ chỉ duyệt món có trạng thái trước hủy là `CHO_BEP`; món đã bắt đầu nấu chỉ admin được duyệt.

## 5. Từ chối yêu cầu

`PUT /api/orders/items/{itemId}/cancel-request/reject`

Role: `WAITER`, `ADMIN`

```json
{
  "ghiChu": "Món đã bắt đầu chế biến"
}
```

Món được khôi phục về `trangThaiTruocHuy`.

## WebSocket

- `/topic/orders`
- `/topic/kitchen`
- `/topic/cashier`
- `/topic/admin/cancellations`
- `/topic/customer/orders/{orderId}`

Sự kiện mới:

- `ORDER_ITEM_CANCELLATION_REQUESTED`
- `ORDER_ITEM_CANCELLED`
- `ORDER_ITEM_CANCELLATION_APPROVED`
- `ORDER_ITEM_CANCELLATION_REJECTED`
