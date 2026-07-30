# API truy xuất an toàn thực phẩm theo lô nguyên liệu

## 1. Công thức món ăn

### Lấy công thức

```http
GET /api/menu/{foodId}/recipe
Authorization: Bearer <ADMIN_TOKEN>
```

### Thay thế toàn bộ công thức

```http
PUT /api/menu/{foodId}/recipe
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "nguyenLieu": [
    { "maNguyenLieu": 1, "dinhLuong": 0.200 },
    { "maNguyenLieu": 3, "dinhLuong": 0.050 }
  ]
}
```

`dinhLuong` là lượng dùng cho **một phần món** và dùng cùng đơn vị với nguyên liệu.
Gửi danh sách rỗng để xóa công thức.

## 2. Cấp phát tự động khi bếp bắt đầu chế biến

Frontend tiếp tục dùng endpoint hiện tại:

```http
PUT /api/orders/items/{itemId}/status
Authorization: Bearer <KITCHEN_TOKEN>
```

```json
{ "trangThaiMon": "DANG_NAU" }
```

Nếu món đã có công thức, backend sẽ trong cùng transaction:

1. Khóa đơn và nguyên liệu.
2. Chọn lô theo FEFO.
3. Chỉ sử dụng lô đang hoạt động, còn hạn và `AN_TOAN`.
4. Trừ tồn kho.
5. Tạo giao dịch `XUAT` với mã lý do `CHE_BIEN_MON`.
6. Lưu lô đã dùng cho chi tiết món.
7. Sau đó mới đổi trạng thái món.

Nếu thiếu nguyên liệu, toàn bộ thao tác rollback và món vẫn ở trạng thái cũ.
Món chưa có công thức vẫn chạy theo quy trình cũ để không làm gián đoạn dữ liệu hiện có.

## 3. Truy xuất từ món trong đơn về lô

```http
GET /api/food-safety/order-items/{itemId}/trace
Authorization: Bearer <ADMIN_TOKEN>
```

## 4. Truy xuất ảnh hưởng từ một lô

```http
GET /api/food-safety/batches/{batchId}/impact
Authorization: Bearer <ADMIN_TOKEN>
```

Kết quả gồm các món, đơn hàng, bàn, thời gian và trạng thái liên quan.

## 5. Báo cáo sự cố và khóa lô

```http
POST /api/food-safety/batches/{batchId}/incidents
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "loaiSuCo": "NGHI_NGO_CHAT_LUONG",
  "mucDo": "CAO",
  "lyDo": "Nguyên liệu có dấu hiệu không đạt chất lượng",
  "ghiChu": "Tạm khóa để kiểm tra nhà cung cấp"
}
```

Mức độ hợp lệ: `THAP`, `TRUNG_BINH`, `CAO`, `KHAN_CAP`.
Khi báo cáo, lô chuyển sang `CO_SU_CO` và không còn được chọn để xuất hoặc chế biến.

## 6. Danh sách sự cố

```http
GET /api/food-safety/incidents
GET /api/food-safety/incidents?batchId={batchId}
Authorization: Bearer <ADMIN_TOKEN>
```

## 7. Xử lý sự cố

```http
PUT /api/food-safety/incidents/{incidentId}/resolve
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

```json
{
  "trangThaiSuCo": "DA_DONG",
  "trangThaiAnToanLo": "AN_TOAN",
  "ketQuaXuLy": "Đã kiểm nghiệm và xác nhận lô đạt yêu cầu"
}
```

Trạng thái sự cố: `DANG_XU_LY`, `DA_DONG`, `DA_THU_HOI`, `DA_TIEU_HUY`.
Trạng thái an toàn lô: `AN_TOAN`, `KHOA_TAM_THOI`, `CO_SU_CO`, `THU_HOI`, `DA_TIEU_HUY`.

## Migration

Có thể để Hibernate cập nhật với `JPA_DDL_AUTO=update`, hoặc chạy thủ công:

```text
database/food_traceability_upgrade.sql
```
