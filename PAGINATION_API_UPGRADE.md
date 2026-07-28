# API phân trang cho các trang quản lý

Các API danh sách cũ được giữ nguyên để không ảnh hưởng các màn hình đang sử dụng. Frontend quản trị nên chuyển sang các endpoint `/page` dưới đây.

## Cấu trúc phản hồi

```json
{
  "success": true,
  "message": "...",
  "data": {
    "content": [],
    "page": 0,
    "size": 10,
    "numberOfElements": 0,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true,
    "empty": true
  }
}
```

- `page` bắt đầu từ `0`.
- `size` được giới hạn từ `1` đến `100`.
- Tìm kiếm, lọc và phân trang đều được thực hiện tại database.

## 1. Quản lý món ăn

```http
GET /api/menu/page?page=0&size=8&keyword=ga&categoryId=2&active=true
```

Tham số tùy chọn: `keyword`, `categoryId`, `active`.

## 2. Quản lý đơn hàng

```http
GET /api/orders/page?page=0&size=10&keyword=12&status=ALL&from=2026-06-01&to=2026-06-23
```

Tham số tùy chọn: `keyword`, `status`, `from`, `to`.

- `from`, `to` dùng định dạng `yyyy-MM-dd` và tính cả ngày `to`.
- Tài khoản bếp vẫn không nhìn thấy đơn `CHO_XAC_NHAN`, giống nghiệp vụ cũ.

## 3. Quản lý nhân viên

```http
GET /api/employees/page?page=0&size=10&keyword=long&role=WAITER&status=DANG_LAM_VIEC
```

Tham số tùy chọn: `keyword`, `role`, `status`.

## 4. Quản lý danh mục

```http
GET /api/categories/page?page=0&size=10&keyword=đồ uống&active=true
```

Tham số tùy chọn: `keyword`, `active`.

## 5. Quản lý khuyến mãi

```http
GET /api/promotions/page?page=0&size=10&keyword=weekend&status=ACTIVE
```

`status` nhận: `ALL`, `ACTIVE`, `UPCOMING`, `ENDED`, `DISABLED`.
