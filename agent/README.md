# Antigravity Microservices Agent Kit

## Mục tiêu
Bộ khung này giúp Antigravity đọc dự án có hệ thống, đối chiếu source với tài liệu, điều tra lỗi theo bằng chứng, thay đổi nhỏ có kiểm soát và giảm nguy cơ ảnh hưởng production.

## Nguyên tắc quan trọng
1. Đây là **khung quy trình**, không phải khẳng định kiến trúc hiện tại của repository.
2. Không tự ý chuyển kiến trúc sang microservices, thêm Kafka/Redis/Eureka/Config Server, đổi database, đổi port hoặc thay đổi deploy chỉ vì tài liệu có nhắc.
3. Source code, cấu hình deploy và dữ liệu runtime hiện tại là bằng chứng; tài liệu có thể lỗi thời. Khi mâu thuẫn, ghi nhận mâu thuẫn và xác minh trước khi sửa.
4. Mặc định chỉ đọc/đề xuất. Mọi thao tác có khả năng ảnh hưởng production phải có phê duyệt rõ ràng.
5. Không tuyên bố đã test/deploy nếu chưa thực sự chạy và kiểm tra kết quả.

## Cách đưa vào repository
1. Sao lưu hoặc commit trạng thái hiện tại trước khi thêm bộ khung.
2. Copy `.agents/`, `docs/`, `prompts/` vào repository.
3. Điền `docs/00-project-context/PROJECT_PROFILE.md`.
4. Điền `docs/01-architecture/AS-IS.md` từ source thật; không copy kiến trúc mục tiêu thành kiến trúc hiện tại.
5. Bật rules phù hợp với công cụ/phiên bản Antigravity đang dùng. Kiểm tra cú pháp rule/agent của Antigravity trước khi dựa vào tính năng tự động; nội dung Markdown vẫn dùng được như hướng dẫn.
6. Chạy prompt `prompts/01-repository-discovery.md` ở chế độ chỉ đọc trước khi giao agent sửa lỗi.

## Mô hình tri thức
- `docs/`: nguồn sự thật của dự án, phải gắn bằng chứng và ngày kiểm tra.
- `.agents/rules/`: luật bắt buộc cho agent.
- `.agents/skills/`: quy trình chuyên môn có thể tái sử dụng.
- `prompts/`: mẫu giao việc.
- `templates/`: mẫu báo cáo, ADR, runbook, postmortem.

## Quy ước trạng thái tài liệu
- `VERIFIED`: đã đối chiếu source/config/runtime hoặc test cụ thể.
- `INFERRED`: suy luận có căn cứ, chưa xác minh đầy đủ.
- `UNKNOWN`: chưa có bằng chứng.
- `STALE`: thông tin có thể đã lỗi thời.
- `CONFLICT`: nguồn thông tin mâu thuẫn.

Không dùng `VERIFIED` nếu không ghi nguồn bằng chứng, đường dẫn file/dòng hoặc lệnh/test và ngày kiểm tra.
