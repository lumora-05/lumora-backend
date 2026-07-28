# Dashboard API upgrade

Backend đã bổ sung các API dành cho trang tổng quan Admin:

- `GET /api/dashboard/recent-orders?limit=5`
- `GET /api/dashboard/recent-activities?limit=5`
- `GET /api/dashboard/charts/top-foods?limit=5`

## Thay đổi chính

- Đơn hàng mới nhất được sắp xếp theo `thoiGianDat` giảm dần.
- Hoạt động gần đây được lưu trong bảng `hoat_dong_he_thong`, không mất khi tải lại trang.
- Top món bán chạy chỉ tính các đơn có trạng thái `DA_THANH_TOAN`.
- Top món bán chạy trả thêm trường `hinhAnh`.
- Số đơn đang chế biến dùng `count(distinct o)` để tránh đếm trùng một đơn có nhiều món.
- Giới hạn `limit` được chặn tối đa 20 cho danh sách gần đây và 50 cho top món.

Nếu `spring.jpa.hibernate.ddl-auto=update`, Hibernate sẽ tự tạo bảng hoạt động. Nếu quản lý schema thủ công, chạy file `database/dashboard_activity_upgrade.sql`.
