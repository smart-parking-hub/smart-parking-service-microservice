# Review trước/sau — fix(booking): Reject overlapping reservations for one vehicle

Ngày review/publish: 2026-11-17 (Asia/Bangkok). Các tên fixture và tài liệu 20261118 được giữ nguyên từ lượt triển khai nguồn.
Owner: SmartParkingHub. Phạm vi: `fix/vehicle-booking-conflicts`; base `d004fa4f78cf`. Chưa deploy.

## Trước và sau

| Trước | Sau |
|---|---|
| Cùng xe có thể tạo nhiều lượt trùng giờ tại cùng/khác bãi | Kiểm tra lịch dưới khóa VEHICLE theo vehicleId, kể cả khi tắt hạn mức giữ chỗ |
| Giờ ra dự kiến đã qua có thể làm mất chặn xe còn trong lượt cũ | PARKING/OVERTIME/chờ quyết toán tiếp tục chặn cho đến khi lượt kết thúc |
| Chưa có quy tắc biên lịch rõ ràng | Khoảng [vào, ra), cho phép lượt nối tiếp đúng giờ; replay requestId giữ nguyên kết quả |

## Review trước push

- Tách đúng phạm vi bằng worktree từ origin/main đã fetch; không gộp thay đổi tính năng khác.
- Đối chiếu contract, ownership, trạng thái, xử lý lỗi và test; không stage secrets, dependency/build output hoặc config kiểm thử local.
- `git diff --check`: đạt. Kết quả kiểm tra sau push (SHA, file scope, CI) được cập nhật trong mô tả PR và báo cáo publish.

## Kiểm chứng

- booking-service: **197 đạt, 4 bỏ qua theo môi trường, 0 lỗi** (201 ca trong JUnit).
- JDK 21; Gradle offline, no-daemon; Kafka listener tắt và broker test trỏ 127.0.0.1:1. Không dùng DB dự án hoặc broker thật.

## Giới hạn và rollout

Không đổi schema, DTO, event hoặc tiền ví. Test đồng thời dùng H2 MySQL mode; chưa xác minh các ca xung đột lịch mới trên MySQL thật trong lượt này. Rollback bằng hoàn tác hai thay đổi logic, không sửa dữ liệu lịch sử.

Không merge/deploy hoặc chuyển tiền thật trong lượt publish này. Working tree nguồn được giữ nguyên; bản sửa review của vé nằm trên nhánh PR riêng.


## Sửa blocker sau push: host UTC

CI đầu trên b6e5a09: 9/24 ca xung đột lịch lỗi. Đã tái hiện cùng 9 lỗi bằng `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` trước sửa (`pr-vehicle-utc-before.log`). Booking đổi default timezone bằng @PostConstruct sau khi persistence có thể đã khởi tạo; dữ liệu Hibernate và so sánh JDBC bị lệch trên host UTC.

Chuyển sang static BeanFactoryPostProcessor trước datasource/Hibernate, theo mẫu Payment Service hiện có. Bổ sung test JPA → JDBC giữ nguyên expected check-in/check-out business wall-clock. Không sửa công thức phí, schema hoặc HTTP contract.

Sau sửa: toàn booking-service dưới UTC có **201 ca: 197 đạt, 4 bỏ qua, 0 lỗi**; nhóm lịch xe có 25 ca. Log `pr-vehicle-utc-after.log`. Đây là thay đổi thứ tự khởi tạo timezone đã có, không phải thêm múi giờ nghiệp vụ mới. Commit sửa nằm trên cùng PR #57; không force push, merge hoặc deploy.
