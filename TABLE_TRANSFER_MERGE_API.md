# API chuyển bàn và ghép bàn

## Quy tắc ghép bàn

- Các bàn chỉ được ghép khi cùng khu vực và chưa thuộc nhóm ghép khác.
- Vẫn hỗ trợ nghiệp vụ cũ: một bàn đang phục vụ có thể ghép thêm bàn `TRONG` chưa có đơn.
- Hỗ trợ thêm nghiệp vụ **hai hoặc nhiều bàn đều đang có đơn** ghép lại để **thanh toán chung một bill**.
- Mỗi bàn tham gia ghép chỉ được có tối đa một đơn đang mở.
- Không cho ghép khi một trong các đơn đã vào `CHO_THANH_TOAN` / `SAN_SANG_THANH_TOAN`; phải ghép trước khi bắt đầu thanh toán.
- Khi ghép các bàn đều đang phục vụ, bàn chính phải là bàn đang có đơn.
- Các đơn gốc và chi tiết món được giữ nguyên. Backend chỉ gán chung `ma_nhom_thanh_toan`, vì vậy trạng thái bếp, hủy món và lịch sử món không bị mất.
- Các đơn đã tồn tại trước lúc ghép vẫn giữ nguyên theo từng bàn để bảo toàn lịch sử món và trạng thái bếp.
- **QR sau khi ghép là một phiên chung:** quét QR của bất kỳ bàn nào trong nhóm đều lấy toàn bộ các đơn đang mở của nhóm.
- Các lượt **gọi thêm từ QR sau khi ghép** được quy về đơn của bàn chính. QR bàn phụ không bị vô hiệu hóa và khách không cần chuyển sang quét QR bàn chính.
- Thao tác nội bộ của nhân viên trên các đơn gốc vẫn giữ tương thích với luồng cũ; bill cuối cùng vẫn tổng hợp theo `ma_nhom_thanh_toan`.

## Thanh toán chung

Ví dụ Bàn 08 có đơn A và Bàn 09 có đơn B:

1. Phục vụ chọn Bàn 08 làm bàn chính và ghép Bàn 09.
2. Backend tạo một `ma_nhom_ban` cho hai bàn và cùng `ma_nhom_thanh_toan` cho hai đơn.
3. Khi khách/phục vụ yêu cầu thanh toán, backend chỉ cho tiếp tục khi **tất cả đơn trong nhóm đã `DA_PHUC_VU`**.
4. Toàn bộ đơn của nhóm cùng chuyển sang `CHO_THANH_TOAN`.
5. Phiếu tạm tính cộng món, tạm tính, khuyến mãi và tiền cọc của tất cả đơn.
6. Tiền mặt hoặc payOS thanh toán **một lần** theo tổng bill.
7. Backend tạo **một hóa đơn chung** gắn với đơn của bàn chính, đồng thời chuyển mọi đơn trong nhóm sang `DA_THANH_TOAN`.
8. Sau khi bill hoàn tất, toàn bộ bàn trong nhóm được giải phóng về `TRONG` và nhóm bàn tự tách.
9. Tra cứu hóa đơn bằng mã của một đơn phụ vẫn trả về hóa đơn chung nhờ `ma_nhom_thanh_toan` được lưu trên đơn.

## Endpoint ghép bàn

`POST /api/tables/merge`

Ví dụ body:

```json
{
  "maBanChinh": 8,
  "maBanGhep": [9]
}
```

Response giữ cấu trúc cũ. Payload realtime có thêm:

- `maDonHangs`: các đơn đang tham gia bill chung.
- `thanhToanChung`: `true` khi nhóm có từ hai đơn đang mở trở lên.

## Chuyển / tách bàn

- Chuyển bàn chỉ thực hiện giữa hai bàn độc lập; nếu bàn đang thuộc nhóm ghép thì phải tách/xử lý nhóm trước.
- Không cho tách thủ công khi nhóm vẫn còn đơn đang mở.
- Một đơn kết thúc/hủy trước không tự làm tách cả nhóm nếu còn đơn khác đang mở.
- Khi đơn cuối cùng/bill chung kết thúc, toàn bộ nhóm tự giải phóng.

### Dữ liệu hóa đơn chung

`GET /api/payments/order/{orderId}` vẫn dùng endpoint cũ. Khi đơn thuộc bill chung, response hóa đơn có thêm các trường transient (không tạo cột DB):

- `maDonHangsThanhToanChung`: danh sách mã các đơn nằm trong bill.
- `chiTietThanhToanChung`: toàn bộ món chưa hủy của các đơn trong bill, dùng để in hóa đơn chung.
- `tenBanThanhToanChung`: ví dụ `Bàn 08 + Bàn 09`.

Các trường cũ vẫn giữ nguyên để frontend hiện tại không bị phá vỡ.
## QR của nhóm bàn ghép

Ví dụ Bàn 07 có đơn `#218`, Bàn 08 có đơn `#219`, sau đó ghép Bàn 07 làm bàn chính:

1. `QR07` và `QR08` đều tiếp tục hoạt động.
2. `GET /api/customer/qr/{qrToken}/orders` từ QR nào cũng trả cùng danh sách đơn đang mở của nhóm (`#218`, `#219`).
3. `GET /api/customer/qr/{qrToken}/orders/current` từ QR nào cũng trả đơn neo của bàn chính (`#218`).
4. Món đã tồn tại trong `#219` không bị di chuyển hoặc xóa.
5. Món khách gọi thêm sau khi ghép từ `QR07` hoặc `QR08` đều được thêm vào đơn đang mở của bàn chính `#218`.
6. Thay đổi ở một đơn trong nhóm phát realtime tới topic của mọi bàn/mọi đơn trong nhóm để các QR đang mở cùng tải lại dữ liệu.
7. Khi thanh toán, `#218` và `#219` vẫn được tổng hợp thành một bill chung như trước.

Nguyên tắc: **QR xác định quyền truy cập vào phiên bàn ghép; bàn chính xác định đơn nhận lượt gọi thêm; `ma_nhom_thanh_toan` xác định bill chung.**
