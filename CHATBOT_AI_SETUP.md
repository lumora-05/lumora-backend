# Thiết lập AI thật cho chatbot LUMORA

## 1. Tạo biến môi trường

Linux/macOS:

```bash
export CHATBOT_AI_ENABLED=true
export OPENAI_API_KEY="sk-..."
export OPENAI_MODEL="gpt-5-mini"
```

Windows PowerShell:

```powershell
$env:CHATBOT_AI_ENABLED="true"
$env:OPENAI_API_KEY="sk-..."
$env:OPENAI_MODEL="gpt-5-mini"
```

Sau đó khởi động lại Spring Boot.

## 2. Kiểm thử

Gửi request:

```http
POST /api/chatbot/messages
Content-Type: application/json
```

```json
{
  "message": "Nhóm mình 4 người, khoảng 800 nghìn, thích hải sản nhưng không ăn cay",
  "sessionId": null,
  "qrToken": null
}
```

Backend sẽ dùng AI để hiểu:

- Intent: `FOOD_RECOMMENDATION`.
- Số khách: `4`.
- Ngân sách tổng: `800000`.
- Sở thích: hải sản.
- Loại trừ: cay.

Danh sách món, giá và tổng tạm tính vẫn được lấy từ database.

## 3. Hội thoại nhiều lượt

Giữ lại `sessionId` backend trả về:

```text
Khách: Gợi ý món cho nhóm mình.
Bot: Nhóm của bạn có bao nhiêu người và ngân sách dự kiến là bao nhiêu?
Khách: 4 người, khoảng 800 nghìn, không ăn cay.
```

AI nhận lịch sử gần nhất để hiểu tin nhắn thứ hai vẫn thuộc yêu cầu gợi ý món.

## 4. Chế độ dự phòng

Chatbot tự dùng rule-based nếu:

- Không có `OPENAI_API_KEY`.
- `CHATBOT_AI_ENABLED=false`.
- OpenAI timeout hoặc trả lỗi.
- Kết quả AI có confidence thấp hơn cấu hình.
- Kết quả không đúng schema hoặc bị từ chối.

Frontend không cần thay đổi endpoint hoặc cấu trúc response.

## 5. Bảo mật

- Không ghi API key vào mã nguồn.
- Không log request Authorization.
- Không cho AI tạo URL hoặc action tùy ý.
- Không gửi dữ liệu thẻ thanh toán, mật khẩu hoặc secret vào hội thoại.
- Chỉ gửi tối đa số tin nhắn lịch sử được cấu hình và đặt `store=false`.

## Kiểm tra truy vấn xếp hạng giá

Sau khi chạy backend, có thể kiểm tra bằng các câu:

```text
Món đắt nhất là món nào?
Món rẻ nhất là món nào?
Ba món đắt nhất.
Món hải sản đắt nhất dưới 200.000đ.
```

Các câu này có bộ nhận diện dự phòng trong backend nên vẫn hoạt động khi OpenAI bị tắt hoặc hết hạn mức. AI chỉ hỗ trợ trích xuất `priceOrder` và `resultLimit`; việc sắp xếp và lấy giá luôn do backend thực hiện trên dữ liệu món thật.
