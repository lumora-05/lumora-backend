# Chatbot hỗ trợ khách hàng

## Phạm vi

Backend kết hợp mô hình AI với dữ liệu thật trong hệ thống:

- Chào hỏi và hướng dẫn sử dụng.
- Giờ mở cửa, địa chỉ, điện thoại, email.
- Tìm món theo tên, danh mục và mức giá.
- Gợi ý một số món đang bán.
- Khuyến mãi đang hoạt động.
- Hướng dẫn đặt bàn.
- Tra cứu trạng thái đơn bằng QR token của bàn.
- Hướng dẫn gọi nhân viên và yêu cầu thanh toán.
- Cảnh báo an toàn đối với dị ứng và nguyên liệu cần tránh.

Phiên bản này có thể gọi OpenAI Responses API để hiểu câu hỏi tự nhiên, giữ ngữ cảnh các lượt gần nhất và trích xuất số khách, ngân sách, sở thích hoặc nguyên liệu cần tránh. AI không được tự tạo món, giá, ưu đãi, trạng thái đơn hay action. Backend luôn truy vấn và kiểm tra lại dữ liệu trong LUMORA trước khi trả cho frontend.

Nếu chưa cấu hình API key, AI bị timeout hoặc dịch vụ AI trả lỗi, chatbot tự động quay về bộ nhận diện intent theo luật hiện có. Hợp đồng API với frontend không thay đổi.

## 1. Gửi câu hỏi

`POST /api/chatbot/messages`

Request:

```json
{
  "message": "Có món nào dưới 200.000đ không?",
  "sessionId": null,
  "qrToken": null
}
```

- `message`: bắt buộc, tối đa 1000 ký tự.
- `sessionId`: gửi lại giá trị backend trả về để tiếp tục cùng một phiên.
- `qrToken`: chỉ cần khi chatbot được mở từ trang khách hàng tại bàn. Backend dùng token này để xác thực trước khi đọc đơn hàng.

Response mẫu:

```json
{
  "success": true,
  "message": "Chatbot đã xử lý câu hỏi thành công",
  "data": {
    "sessionId": "2e2f1bb8-3b6e-4c69-9fc0-bb83a299d6ae",
    "intent": "FOOD_SEARCH",
    "message": "Tôi tìm thấy 3 món phù hợp trong mức tối đa 200.000đ.",
    "foods": [
      {
        "id": 12,
        "name": "Mì Ý sốt kem nấm",
        "price": 159000.00,
        "description": "...",
        "imageUrl": "/uploads/foods/mi-y.jpg",
        "category": "Món chính",
        "available": true
      }
    ],
    "promotions": [],
    "order": null,
    "actions": [
      {
        "label": "Xem thực đơn",
        "action": "OPEN_MENU",
        "url": "/#menu",
        "payload": {}
      }
    ],
    "quickReplies": [
      "Gợi ý món cho tôi",
      "Có món nào dưới 200.000đ?",
      "Ưu đãi hiện tại",
      "Nhà hàng mở cửa lúc nào?",
      "Tôi muốn đặt bàn"
    ],
    "disclaimer": null
  }
}
```

## 2. Câu hỏi nhanh

`GET /api/chatbot/quick-replies`

Trả danh sách gợi ý để frontend hiển thị dưới dạng chip.

## 3. Intent trả về

- `GREETING`
- `OPENING_HOURS`
- `CONTACT_INFO`
- `FOOD_SEARCH`
- `FOOD_RECOMMENDATION`
- `PROMOTION_SEARCH`
- `RESERVATION_SUPPORT`
- `ORDER_STATUS`
- `CALL_WAITER`
- `PAYMENT_SUPPORT`
- `ALLERGY_SAFETY`
- `UNKNOWN`

## 4. Action dành cho frontend

- `OPEN_MENU`: mở khu vực/trang thực đơn.
- `OPEN_RESERVATION`: mở trang đặt bàn.
- `CALL_RESTAURANT`: mở liên kết `tel:`.
- `OPEN_CURRENT_ORDER`: mở đơn hiện tại của bàn.
- `OPEN_SERVICE_REQUEST`: mở chức năng gọi phục vụ.
- `REQUIRE_TABLE_QR`: thông báo khách cần quét QR tại bàn.

Backend không tự tạo yêu cầu gọi phục vụ, đặt bàn hay thanh toán từ nội dung chat. Frontend phải chuyển người dùng đến giao diện tương ứng và yêu cầu xác nhận trước khi gọi API nghiệp vụ.

## 5. Biến môi trường

### Thông tin nhà hàng

```properties
RESTAURANT_NAME=LUMORA
RESTAURANT_ADDRESS=...
RESTAURANT_PHONE=...
RESTAURANT_EMAIL=...
RESTAURANT_OPENING_HOURS=...
RESTAURANT_RESERVATION_URL=/reservations
RESTAURANT_MENU_URL=/#menu
```

Nếu thông tin chưa được cấu hình, chatbot sẽ nói rõ là chưa có dữ liệu thay vì tự tạo câu trả lời.

### AI thật bằng OpenAI

```properties
CHATBOT_AI_ENABLED=true
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-5-mini
OPENAI_BASE_URL=https://api.openai.com/v1
CHATBOT_AI_TIMEOUT_SECONDS=20
CHATBOT_AI_MAX_OUTPUT_TOKENS=700
CHATBOT_AI_MAX_HISTORY_MESSAGES=8
CHATBOT_AI_MIN_CONFIDENCE=0.45
```

- Chỉ đặt `OPENAI_API_KEY` ở biến môi trường của backend.
- Không đưa API key vào React, Git, file ZIP hoặc `application.properties`.
- `CHATBOT_AI_ENABLED=false` sẽ tắt AI và dùng hoàn toàn rule-based.
- Khi AI không khả dụng, request vẫn được xử lý bằng cơ chế dự phòng.
- Backend gửi `store=false` khi gọi Responses API.

## 6. Cách AI được sử dụng

```text
Tin nhắn + lịch sử gần nhất
→ OpenAI trả intent và ràng buộc theo JSON Schema
→ Backend kiểm tra confidence
→ Backend truy vấn món/ưu đãi/đơn hàng thật
→ Backend kiểm tra giá, trạng thái và QR token
→ Trả ChatbotResponse cũ cho frontend
```

AI hỗ trợ:

- Hiểu câu hỏi tự nhiên và lỗi diễn đạt thông thường.
- Hiểu câu trả lời nối tiếp trong cùng `sessionId`.
- Trích xuất số khách, ngân sách tổng hoặc giới hạn từng món.
- Trích xuất sở thích, từ khóa món và nguyên liệu cần tránh.
- Hỏi lại khi yêu cầu gợi ý món còn thiếu thông tin quan trọng.

AI không được phép:

- Tự tạo món hoặc giá.
- Tự tạo khuyến mãi.
- Tự xác nhận đặt bàn.
- Tự gửi yêu cầu gọi phục vụ hoặc thanh toán.
- Cam kết món an toàn tuyệt đối với dị ứng.

## 7. Lịch sử hội thoại

- `phien_chatbot`: lưu mã phiên, QR token và thời gian hoạt động.
- `tin_nhan_chatbot`: lưu tin nhắn người dùng/trợ lý, intent và metadata tối thiểu.

Không lưu API key, dữ liệu thẻ thanh toán hoặc mật khẩu trong lịch sử chat.

## 8. Tìm món theo thứ tự giá

Chatbot hỗ trợ xếp hạng giá ngay cả khi dịch vụ AI không khả dụng:

```text
Món đắt nhất là món nào?
Món rẻ nhất là món nào?
Ba món đắt nhất.
Top 5 món có giá thấp nhất.
Món hải sản đắt nhất dưới 200.000đ.
```

Quy tắc xử lý:

- `HIGHEST`: sắp xếp giá giảm dần.
- `LOWEST`: sắp xếp giá tăng dần.
- Nếu không nêu số lượng, backend trả một món.
- Nếu nêu số lượng bằng chữ hoặc số, backend trả tối đa 10 món.
- Các từ xếp hạng như `đắt nhất`, `rẻ nhất`, `giá cao nhất` không được dùng làm từ khóa tìm tên món.
- Chỉ các món đang hoạt động và có giá hợp lệ mới được đưa vào kết quả.
- Frontend không cần thay đổi vì kết quả vẫn trả trong trường `foods` hiện có.
