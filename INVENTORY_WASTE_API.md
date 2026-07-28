# Tiêu hủy, hao hụt và tồn khả dụng

Tất cả API yêu cầu JWT của tài khoản `ADMIN`.

## 1. Nguyên tắc tồn kho

Backend giữ `soLuongTon` là **tồn vật lý** để tương thích dữ liệu cũ. Phản hồi nguyên liệu bổ sung:

- `soLuongTonVatLy`: số lượng thực tế còn nằm trong kho.
- `soLuongKhaDung`: phần còn hạn, lô đang hoạt động và được phép xuất sử dụng.
- `soLuongChoTieuHuy`: phần thuộc các lô đã hết hạn nhưng chưa xác nhận tiêu hủy.
- `giaTriTonKhaDung` và `giaTriChoTieuHuy`.

`trangThaiTonKho` và cảnh báo sắp hết/hết hàng được tính theo `soLuongKhaDung`, không tính hàng hết hạn.

Phản hồi lô bổ sung:

- `soLuongKhaDung`, `soLuongChoTieuHuy`.
- `giaTriKhaDung`, `giaTriChoTieuHuy`.
- `choPhepXuat`, `choPhepTieuHuy`.

## 2. Danh sách lý do

`GET /api/ingredients/waste/reasons`

Các mã hiện có:

- `QUA_HAN_SU_DUNG`
- `HU_HONG`
- `BAO_QUAN_KHONG_DAT`
- `DO_VO`
- `KIEM_KE_THIEU`
- `CHE_BIEN_LOI`
- `KHAC`

`KHAC` bắt buộc nhập `ghiChu`. `QUA_HAN_SU_DUNG` chỉ hợp lệ với lô đã hết hạn.

## 3. Tiêu hủy một lô

`POST /api/ingredients/batches/{batchId}/dispose?warningDays=3`

```json
{
  "soLuong": 8,
  "maLyDo": "QUA_HAN_SU_DUNG",
  "ghiChu": "Kiểm kê kho đầu ngày"
}
```

Backend thực hiện trong một transaction:

1. Khóa nguyên liệu và lô để tránh xử lý đồng thời.
2. Kiểm tra số lượng không vượt tồn vật lý và tồn lô.
3. Giảm `so_luong_con_lai` của lô.
4. Giảm `so_luong_ton` vật lý của nguyên liệu.
5. Tạo giao dịch `TIEU_HUY` có mã lý do, ghi chú, người thực hiện và giá trị hao hụt.
6. Cập nhật cảnh báo tồn khả dụng.

Không xóa lô hoặc lịch sử giao dịch.

## 4. Ghi nhận hao hụt nguyên liệu

`POST /api/ingredients/{ingredientId}/waste?warningDays=3`

### Theo lô

```json
{
  "maLo": 12,
  "soLuong": 1.5,
  "maLyDo": "HU_HONG",
  "ghiChu": "Bao bì bị rách"
}
```

### Phần tồn cũ chưa theo dõi theo lô

```json
{
  "soLuong": 2,
  "maLyDo": "KIEM_KE_THIEU",
  "ghiChu": "Chênh lệch khi kiểm kê cuối ngày"
}
```

Không thể dùng lý do `QUA_HAN_SU_DUNG` nếu không chọn lô.

## 5. Lịch sử và báo cáo

Lịch sử tiêu hủy dùng API giao dịch hiện có:

`GET /api/ingredients/transactions/page?type=TIEU_HUY&page=0&size=10`

Phản hồi giao dịch bổ sung:

- `giaTriGiaoDich`
- `maLyDo`
- `ghiChu`

Thống kê hao hụt:

`GET /api/ingredients/waste/statistics?from=2026-07-01&to=2026-07-31`

Trả về:

- Số lần tiêu hủy.
- Số nguyên liệu và số lô bị ảnh hưởng.
- Tổng giá trị tiêu hủy.
- Thống kê số lần và giá trị theo từng lý do.

## 6. Nâng cấp database

Chạy file:

`database/inventory_waste_upgrade.sql`

File thêm `ma_ly_do`, `ghi_chu` và mở rộng constraint `loai_giao_dich` để nhận `TIEU_HUY`. Hibernate `ddl-auto=update` có thể thêm cột nhưng thường không tự sửa check constraint cũ, vì vậy cần chạy migration này trên database đã tồn tại.
