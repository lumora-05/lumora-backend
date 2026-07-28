# API phân trang thực đơn khách hàng

API thông tin bàn cũ `GET /api/customer/tables/{tableId}` vẫn được giữ nguyên để không ảnh hưởng frontend hiện tại.

## Endpoint mới

```http
GET /api/customer/tables/{tableId}/menu?page=0&size=12&keyword=ga&categoryId=2
```

- `page`: số trang, bắt đầu từ `0`.
- `size`: số món mỗi trang, mặc định `12`, giới hạn từ `1` đến `100`.
- `keyword`: tùy chọn, tìm theo tên món, mô tả hoặc tên danh mục.
- `categoryId`: tùy chọn, lọc theo danh mục.
- Chỉ trả món có `trangThai = true`.
- Backend kiểm tra bàn/QR hợp lệ trước khi trả thực đơn.

## Cấu trúc phản hồi

```json
{
  "success": true,
  "message": "Lấy thực đơn phân trang cho khách hàng thành công",
  "data": {
    "content": [],
    "page": 0,
    "size": 12,
    "numberOfElements": 12,
    "totalElements": 40,
    "totalPages": 4,
    "first": true,
    "last": false,
    "empty": false
  }
}
```
