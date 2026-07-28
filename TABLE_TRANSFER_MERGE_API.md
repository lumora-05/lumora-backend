# API chuyển bàn và ghép bàn

## Nguyên tắc nghiệp vụ

- Một nhóm bàn có đúng một `maBanChinh` và dùng chung một đơn đang mở.
- Các bàn chỉ được ghép khi cùng khu vực.
- Bàn chính có thể đang trống hoặc đang có đúng một đơn mở.
- Bàn ghép phải đang `TRONG` và không có đơn mở.
- QR của bàn phụ vẫn dùng được; backend tự quy về bàn chính khi tạo đơn, gọi thêm món hoặc lấy đơn hiện tại.
- Thanh toán hoặc hủy đơn cuối cùng sẽ tự đưa toàn bộ nhóm về `TRONG` và xóa thông tin ghép.
- Chuyển bàn chỉ thực hiện giữa hai bàn độc lập; nếu bàn đang ghép thì phải tách nhóm trước.

## Trường dữ liệu mới của bàn

```json
{
  "maNhomBan": "uuid-cua-nhom",
  "maBanChinh": 3,
  "dangGhepBan": true,
  "laBanChinh": false,
  "vaiTroTrongNhom": "BAN_GHEP"
}
```

## 1. Chuyển bàn

`POST /api/tables/{maBanNguon}/transfer`

Quyền: `ADMIN`, `WAITER`

```json
{
  "maBanDich": 8
}
```

Điều kiện:

- Bàn nguồn có đúng một đơn đang mở.
- Bàn đích đang `TRONG`, không có đơn mở và không thuộc nhóm ghép.
- Phục vụ chỉ thao tác trên các bàn trong khu vực được phân công.

## 2. Ghép bàn

`POST /api/tables/merge`

Quyền: `ADMIN`, `WAITER`

```json
{
  "maBanChinh": 3,
  "maBanGhep": [4, 5]
}
```

Sau khi ghép, đơn luôn được lưu tại bàn chính. QR của Bàn 04 hoặc Bàn 05 vẫn truy cập và gọi thêm vào đơn của Bàn 03.

## 3. Tách nhóm bàn

`DELETE /api/tables/groups/{maNhomBan}`

Quyền: `ADMIN`, `WAITER`

Chỉ tách thủ công khi nhóm không có đơn đang mở. Khi thanh toán hoặc hủy đơn cuối cùng, backend tự tách nhóm.

## Realtime

Các thao tác phát sự kiện tới:

- `/topic/tables`
- `/topic/orders`
- `/topic/customer/tables/{maBan}` của từng bàn liên quan

Loại sự kiện:

- `TABLE_TRANSFERRED`
- `TABLES_MERGED`
- `TABLES_UNMERGED`
