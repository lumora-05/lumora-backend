# Phiếu tạm tính có VietQR

Frontend thu ngân đang sử dụng ba API sau:

```http
GET /api/payments/payment-slip/order/{orderId}
GET /api/payments/vietqr/order/{orderId}
POST /api/payments
```

Hai API GET chỉ đọc dữ liệu. Chúng không tạo hóa đơn, không đổi trạng thái đơn và không giải phóng bàn.

`POST /api/payments` là bước xác nhận cuối cùng sau khi thu ngân đã nhận tiền.

## Dữ liệu thanh toán

Tiền mặt:

```json
{
  "maDonHang": 25,
  "phuongThucThanhToan": "TIEN_MAT",
  "tienKhachDua": 200000,
  "maGiaoDich": null,
  "ghiChu": null
}
```

Chuyển khoản:

```json
{
  "maDonHang": 25,
  "phuongThucThanhToan": "CHUYEN_KHOAN",
  "tienKhachDua": null,
  "maGiaoDich": null,
  "ghiChu": "Đã kiểm tra tiền vào tài khoản"
}
```

## Cấu hình VietQR

```text
VIETQR_BANK_ID=970422
VIETQR_BANK_NAME=MB Bank
VIETQR_ACCOUNT_NO=<so-tai-khoan>
VIETQR_ACCOUNT_NAME=<ten-chu-tai-khoan>
VIETQR_TEMPLATE=compact2
VIETQR_DESCRIPTION_PREFIX=LUMORA
```
