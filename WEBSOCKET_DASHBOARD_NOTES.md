# Bổ sung WebSocket realtime và Dashboard biểu đồ

## 1. WebSocket realtime

Backend đã bổ sung STOMP WebSocket tại endpoint:

```text
/ws
```

Frontend React có thể kết nối bằng SockJS + STOMP:

```bash
npm i sockjs-client @stomp/stompjs
```

Ví dụ lắng nghe realtime:

```js
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  reconnectDelay: 5000,
  onConnect: () => {
    client.subscribe('/topic/orders', message => console.log('Orders:', JSON.parse(message.body)));
    client.subscribe('/topic/kitchen', message => console.log('Kitchen:', JSON.parse(message.body)));
    client.subscribe('/topic/cashier', message => console.log('Cashier:', JSON.parse(message.body)));
    client.subscribe('/topic/payments', message => console.log('Payments:', JSON.parse(message.body)));
    client.subscribe('/topic/dashboard', message => console.log('Dashboard refresh:', JSON.parse(message.body)));
  }
});

client.activate();
```

### Các topic đã có

| Topic | Dùng cho | Khi nào bắn sự kiện |
|---|---|---|
| `/topic/orders` | Nhân viên phục vụ / màn hình quản lý đơn | Có đơn mới, đổi trạng thái đơn, món đổi trạng thái, thanh toán |
| `/topic/kitchen` | Nhân viên bếp | Có món mới, món đổi trạng thái |
| `/topic/cashier` | Thu ngân | Đơn đổi trạng thái |
| `/topic/payments` | Thu ngân / Admin | Thanh toán thành công |
| `/topic/dashboard` | Admin Dashboard | Có dữ liệu mới cần reload biểu đồ |

### Cấu trúc message realtime

```json
{
  "type": "NEW_ORDER",
  "message": "Có đơn hàng mới",
  "data": {},
  "createdAt": "2026-06-15T10:30:00"
}
```

## 2. API Dashboard biểu đồ

Các API dưới đây yêu cầu role `ADMIN`.

### Tổng quan dashboard

```http
GET /api/dashboard
```

### Doanh thu 7 ngày gần nhất

```http
GET /api/dashboard/revenue/last-7-days
```

### Biểu đồ doanh thu theo khoảng ngày

```http
GET /api/dashboard/charts/revenue?from=2026-06-01&to=2026-06-15
```

Nếu không truyền `from`, `to` thì mặc định lấy 30 ngày gần nhất.

Response gồm:

```json
{
  "ngay": "2026-06-15",
  "doanhThu": 1500000,
  "soHoaDon": 8,
  "soDonHang": 12
}
```

### Biểu đồ trạng thái đơn hàng

```http
GET /api/dashboard/charts/order-status
```

Response gồm:

```json
{
  "trangThai": "CHO_XAC_NHAN",
  "soLuong": 5
}
```

### Top món bán chạy

```http
GET /api/dashboard/charts/top-foods?from=2026-06-01&to=2026-06-15&limit=10
```

Response gồm:

```json
{
  "maMonAn": 1,
  "tenMonAn": "Cơm gà",
  "soLuongBan": 20,
  "doanhThu": 900000
}
```
