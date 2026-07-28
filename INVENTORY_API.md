# Kho nguyên liệu, lô và hạn sử dụng

Tất cả API yêu cầu JWT của tài khoản `ADMIN`.

## 1. Danh sách và thống kê nguyên liệu

- `GET /api/ingredients`
- `GET /api/ingredients/active`
- `GET /api/ingredients/page?page=0&size=10&keyword=&active=true&stockStatus=SAP_HET`
- `GET /api/ingredients/{id}`
- `GET /api/ingredients/low-stock`
- `GET /api/ingredients/statistics`

`stockStatus`: `CON_HANG`, `SAP_HET`, `HET_HANG`.

## 2. Thêm nguyên liệu

`POST /api/ingredients`

```json
{
  "tenNguyenLieu": "Thịt bò",
  "donViTinh": "kg",
  "soLuongTon": 0,
  "mucTonToiThieu": 5,
  "giaNhap": 220000,
  "moTa": "Bảo quản ngăn mát",
  "trangThai": true
}
```

Khi dùng quản lý hạn sử dụng, nên tạo nguyên liệu với `soLuongTon = 0`, sau đó nhập kho bằng lô. Số tồn ban đầu nhập trực tiếp trong nguyên liệu vẫn được hỗ trợ để tương thích dữ liệu cũ nhưng sẽ được xem là phần tồn chưa theo dõi theo lô.

## 3. Cập nhật và ngừng sử dụng

- `PUT /api/ingredients/{id}`: khi `soLuongTon` là `null`, backend giữ nguyên số tồn hiện tại.
- `DELETE /api/ingredients/{id}`: chuyển `trangThai=false`, không xóa lịch sử.

Nếu nguyên liệu đã có lô, tồn kho tổng không được điều chỉnh thấp hơn tổng số lượng còn lại của các lô.

## 4. Lô nguyên liệu và hạn sử dụng

### Danh sách lô phân trang

`GET /api/ingredients/batches/page?page=0&size=10&keyword=&ingredientId=1&active=true&expiryStatus=SAP_HET_HAN&from=2026-07-01&to=2026-07-31&warningDays=3`

`expiryStatus`:

- `CON_HAN`
- `SAP_HET_HAN`
- `HET_HAN`
- `DA_DUNG_HET`
- `KHONG_THEO_DOI`

`warningDays` là số ngày được xem là sắp hết hạn, mặc định `3`, tối đa `365`.

### Danh sách lô của một nguyên liệu

`GET /api/ingredients/{ingredientId}/batches?warningDays=3`

Danh sách được sắp xếp để hỗ trợ FEFO: lô có hạn sử dụng gần nhất đứng trước, lô không theo dõi hạn đứng sau.

### Chi tiết lô

`GET /api/ingredients/batches/{batchId}?warningDays=3`

### Nhập một lô mới

`POST /api/ingredients/{ingredientId}/batches`

```json
{
  "soLo": "TB-20260701-01",
  "ngayNhap": "2026-07-01",
  "ngaySanXuat": "2026-06-30",
  "hanSuDung": "2026-07-05",
  "soLuongNhap": 10,
  "donGiaNhap": 220000,
  "nhaCungCap": "Công ty Thực phẩm ABC",
  "ghiChu": "Nhập theo phiếu NK-001"
}
```

`hanSuDung` có thể để trống đối với nguyên liệu không cần theo dõi hạn. Backend không cho nhập lô đã hết hạn, không cho ngày sản xuất sau ngày nhập và không cho hạn sử dụng trước ngày nhập.

### Sửa thông tin lô

`PUT /api/ingredients/batches/{batchId}`

```json
{
  "soLo": "TB-20260701-01",
  "ngayNhap": "2026-07-01",
  "ngaySanXuat": "2026-06-30",
  "hanSuDung": "2026-07-05",
  "donGiaNhap": 220000,
  "nhaCungCap": "Công ty Thực phẩm ABC",
  "trangThai": true
}
```

Sửa lô không thay đổi số lượng. Muốn kiểm kho một lô, dùng API cập nhật tồn kho với `maLo`.

### Ngừng sử dụng lô

`DELETE /api/ingredients/batches/{batchId}`

Chỉ được ngừng sử dụng khi số lượng còn lại bằng `0` để tránh mất dấu hàng đang tồn.

### Thống kê hạn sử dụng

`GET /api/ingredients/batches/statistics?warningDays=3`

Trả về tổng số lô, lô đang sử dụng, sắp hết hạn, đã hết hạn, đã dùng hết, không theo dõi hạn và giá trị hàng đã hết hạn.

## 5. Nhập, xuất và kiểm kho

`PATCH /api/ingredients/{ingredientId}/stock`

API cũ vẫn hoạt động. Các trường lô mới đều là tùy chọn.

### Nhập kho không theo lô — tương thích frontend cũ

```json
{
  "loaiGiaoDich": "NHAP",
  "soLuong": 10,
  "donGiaNhap": 225000,
  "lyDo": "Nhập kho thông thường"
}
```

### Nhập kho và tạo lô qua API tồn kho

```json
{
  "loaiGiaoDich": "NHAP",
  "soLuong": 10,
  "donGiaNhap": 225000,
  "lyDo": "Nhập theo phiếu NK-002",
  "soLo": "TB-20260703-01",
  "ngayNhap": "2026-07-03",
  "ngaySanXuat": "2026-07-02",
  "hanSuDung": "2026-07-08",
  "nhaCungCap": "Công ty Thực phẩm ABC"
}
```

### Nhập thêm vào lô hiện có

```json
{
  "loaiGiaoDich": "NHAP",
  "soLuong": 2,
  "maLo": 5,
  "donGiaNhap": 225000,
  "lyDo": "Bổ sung cùng lô"
}
```

### Xuất kho tự động theo FEFO

```json
{
  "loaiGiaoDich": "XUAT",
  "soLuong": 2.5,
  "lyDo": "Xuất sử dụng"
}
```

Backend tự trừ lô còn hạn có hạn sử dụng gần nhất trước. Lô đã hết hạn bị chặn không cho xuất sử dụng. Nếu số lượng nằm ở nhiều lô, lịch sử kho ghi riêng từng lô đã bị trừ.

### Xuất từ lô được chọn

```json
{
  "loaiGiaoDich": "XUAT",
  "soLuong": 1,
  "maLo": 5,
  "lyDo": "Xuất lô được chọn"
}
```

### Kiểm kho toàn bộ nguyên liệu

`soLuong` là tồn tổng thực tế mới. Không được thấp hơn tổng tồn đang theo dõi theo lô.

```json
{
  "loaiGiaoDich": "DIEU_CHINH",
  "soLuong": 17.75,
  "lyDo": "Kiểm kho cuối ngày"
}
```

### Kiểm kho một lô

`soLuong` là số lượng thực tế còn lại của lô.

```json
{
  "loaiGiaoDich": "DIEU_CHINH",
  "soLuong": 2.75,
  "maLo": 5,
  "lyDo": "Kiểm đếm lại lô TB-20260701-01"
}
```

Có thể dùng điều chỉnh lô về `0` để ghi nhận hủy hàng hỏng hoặc hàng hết hạn. Giao dịch vẫn được lưu trong lịch sử.

## 6. Lịch sử nhập xuất

`GET /api/ingredients/transactions/page?page=0&size=10&ingredientId=1&batchId=5&type=NHAP&from=2026-06-01&to=2026-06-30`

`type`: `NHAP`, `XUAT`, `DIEU_CHINH`.

Phản hồi lịch sử có thêm `maLo`, `soLo`, `hanSuDung`. Các giao dịch cũ hoặc tồn không theo lô trả ba trường này là `null`.

## 7. Tiêu hủy, hao hụt và tồn khả dụng

Backend đã bổ sung giao dịch riêng `TIEU_HUY`; không cần dùng `DIEU_CHINH` để biểu diễn tiêu hủy.

- `GET /api/ingredients/waste/reasons`
- `POST /api/ingredients/batches/{batchId}/dispose`
- `POST /api/ingredients/{ingredientId}/waste`
- `GET /api/ingredients/waste/statistics?from=&to=`
- `GET /api/ingredients/transactions/page?type=TIEU_HUY`

Phản hồi nguyên liệu tách rõ `soLuongTonVatLy`, `soLuongKhaDung` và `soLuongChoTieuHuy`. Trạng thái sắp hết/hết hàng được tính theo tồn khả dụng nên hàng quá hạn không còn làm sai cảnh báo kho.

Chi tiết request, lý do và migration xem `INVENTORY_WASTE_API.md` và `database/inventory_waste_upgrade.sql`.
