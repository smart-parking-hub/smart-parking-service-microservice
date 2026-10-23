# Antigravity Microservices Agent System (.agents)

## Mục tiêu
Thư mục `.agents/` là trung tâm cấu hình tri thức chính thức cho các AI Agent (Antigravity IDE / Gemini AI) trong hệ sinh thái **SmartParkingHub Microservices**.

Hệ thống tuân thủ cấu trúc Customization chuẩn của Antigravity AI:
- `.agents/rules/`: Các quy chuẩn, luật bất khả xâm phạm và chuẩn mực kiến trúc.
- `.agents/skills/`: Kỹ năng chuyên sâu được nạp theo ngữ cảnh.
- `.agents/workflows/`: Quy trình xử lý kích hoạt qua lệnh Slash.
- `.agents/templates/`: Mẫu báo cáo sự cố, đề xuất thay đổi và ADR.
- `.agents/prompts/`: Mẫu prompt chuẩn để giao việc cho các Persona.
- `.agents/mcp_config.json`: Cấu hình kết nối Model Context Protocol.

## Nguyên tắc cốt lõi
1. **Luôn kiểm tra bằng chứng trước khi sửa đổi:** Source code và dữ liệu runtime là nguồn sự thật tối thượng.
2. **Quy tắc an toàn Production:** Mặc định chế độ Read-Only trên VPS / môi trường thực.
3. **Database-per-Service:** Tuyệt đối không truy vấn chéo database giữa các Microservices.
