# payOS - tự động xác nhận thanh toán VietQR

## Phạm vi thay đổi

Luồng thanh toán chuyển khoản **đơn tại bàn** đã được đổi từ xác nhận thủ công sang payOS webhook.
Tiền mặt và các nghiệp vụ khác giữ nguyên.

## Biến môi trường cần thêm trên Render

```text
PAYOS_CLIENT_ID=<Client ID của kênh thanh toán payOS>
PAYOS_API_KEY=<API Key>
PAYOS_CHECKSUM_KEY=<Checksum Key>
PAYOS_RETURN_URL=https://<frontend-domain>/
PAYOS_CANCEL_URL=https://<frontend-domain>/
PAYOS_WEBHOOK_URL=https://<backend-domain>/api/payments/payos/webhook
PAYOS_EXPIRE_MINUTES=15
```

## Webhook

URL backend:

```text
POST /api/payments/payos/webhook
```

Endpoint này không dùng JWT vì payOS gọi trực tiếp. Backend xác minh chữ ký HMAC-SHA256 bằng `PAYOS_CHECKSUM_KEY` trước khi xử lý.

Sau khi deploy backend và cấu hình `PAYOS_WEBHOOK_URL`, đăng nhập ADMIN rồi gọi một lần:

```text
POST /api/payments/payos/register-webhook
```

Backend sẽ gọi API `confirm-webhook` của payOS với URL:

```text
https://<backend-domain>/api/payments/payos/webhook
```

Có thể đăng ký lại endpoint này khi đổi domain webhook.

## Luồng mới

1. Thu ngân mở VietQR của đơn đang `CHO_THANH_TOAN` / `SAN_SANG_THANH_TOAN`.
2. Backend tạo payment request payOS và trả ảnh QR cho frontend bằng đúng field `qrUrl` cũ.
3. Khách chuyển khoản.
4. payOS gửi webhook về backend.
5. Backend kiểm tra chữ ký, orderCode, paymentLinkId và số tiền.
6. Backend tự tạo hóa đơn, ghi mã tham chiếu giao dịch, cập nhật đơn `DA_THANH_TOAN`, xử lý điểm/cọc, giải phóng bàn và gửi realtime notification.
7. Nếu webhook bị gửi lại, backend xử lý idempotent và không tạo hóa đơn lần hai.

## Thay đổi quan trọng

`POST /api/payments` vẫn dùng cho **TIEN_MAT**. Nếu frontend cố gửi `CHUYEN_KHOAN` để xác nhận thủ công, backend sẽ từ chối với thông báo rằng chuyển khoản phải chờ webhook payOS.

## Đặt món trực tuyến / giao hàng

Luồng VietQR của đơn online cũng dùng payOS tự động:

1. Khách tạo đơn với phương thức `VIETQR`.
2. `GET /api/customer/delivery/orders/{trackingToken}/vietqr` tạo payment request payOS và trả QR qua field `qrUrl` cũ.
3. Khách chuyển khoản.
4. payOS gọi `POST /api/payments/payos/webhook`.
5. Backend xác minh chữ ký, `orderCode`, `paymentLinkId` và số tiền rồi tự cập nhật `trangThaiThanhToan = DA_THANH_TOAN`.
6. Đơn chuyển từ `CHO_THANH_TOAN` sang `CHO_XAC_NHAN`; Thu ngân/Admin chỉ còn xác nhận **đơn hàng**, không phải xác nhận tiền thủ công.
7. Nhân viên xác nhận đơn được lưu làm người phụ trách để tạo hóa đơn khi đơn hoàn tất.

Để hỗ trợ QR do khách tự mở, cột `giao_dich_payos.ma_nhan_vien` phải cho phép `NULL`. Nếu database hiện tại đã có bảng này, chạy `database/payos_delivery_auto_upgrade.sql` một lần (hoặc để Hibernate schema update áp dụng thay đổi nullable nếu môi trường của bạn hỗ trợ).
