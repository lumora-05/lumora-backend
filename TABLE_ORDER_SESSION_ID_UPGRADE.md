# Table order sessionId upgrade

## Mục tiêu
Phân biệt từng lượt khách sử dụng cùng một bàn/QR mà không đổi QR cố định của bàn.

## Hành vi
- Mỗi `Order` tại bàn có thêm `sessionId` (UUID).
- Khi tạo đơn tại bàn mới, backend tự sinh `sessionId`.
- Khi khách gọi thêm món vào cùng đơn, `sessionId` giữ nguyên.
- Sau khi đơn cũ kết thúc và lượt khách mới tạo đơn mới, backend sinh `sessionId` mới.
- Đơn giao hàng không có bàn nên không sinh `sessionId`.
- API `/api/orders/waiter/active` trả thêm `sessionId` để trang Phục vụ có thể hiển thị nếu cần.
- API Bếp không thay đổi payload tối ưu hiện tại.

## Database
Mặc định `spring.jpa.hibernate.ddl-auto=update` sẽ thêm cột tự động. Nếu production tắt schema update, chạy `database/table_order_session_id_upgrade.sql`.
