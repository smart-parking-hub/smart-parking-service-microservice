# 📁 SMARTPARKINGHUB - HỆ THỐNG SCRIPTS ĐIỀU PHỐI

Toàn bộ các kịch bản tự động hóa, build, test, vận hành container, quản lý task Trello và công cụ quản trị Git đã được sắp xếp gọn gàng vào 5 thư mục chuyên biệt:

---

## 🗂️ Cấu Trúc 5 Nhóm Thư Mục Chuyên Biệt

```
smart-parking-microservice/scripts/
│
├── 📂 dev/                         # [1] Môi Trường & Điều Phối Container Docker
│   ├── start-infra.bat             # Khởi động MySQL, Redis, Kafka, Prometheus, Grafana
│   ├── start-local-dev.bat         # Build/recreate hồ sơ Local Dev
│   ├── start-local-full.bat        # Khởi động Full Local Lab (toàn bộ services + infra)
│   ├── start-services.bat          # Build JAR và chạy container microservices
│   ├── start-vps-r4.bat            # Khởi động cấu hình giới hạn RAM 512MB VPS
│   ├── kill-ports.bat              # Quét và tắt các tiến trình chiếm dụng port
│   └── kill-ports.ps1              # Core PowerShell xử lý kill port
│
├── 📂 build-test/                  # [2] Biên Dịch, Kiểm Thử & Di Trú Cơ Sở Dữ Liệu
│   ├── build-all.bat               # Build toàn bộ dự án Gradle (-x test)
│   ├── test-all.bat                # Chạy trọn bộ Unit Test & Integration Test
│   ├── verify-all.bat              # Vòng lặp kiểm tra: Compile + Test + Syntax Check
│   ├── lint-check.bat              # Kiểm tra coding convention & vi phạm syntax
│   └── db-migrate-dry-run.bat      # Kiểm tra file đọc được; không xác thực SQL/Flyway
│
├── 📂 quality/                     # [3] Chất Lượng Mã Nguồn & Báo Cáo SonarQube
│   ├── start-sonarqube.bat         # Khởi động SonarQube container (Port 9000)
│   ├── sonar-scan.bat              # Chạy test + JaCoCo coverage + Sonar scanner
│   ├── get-coverage-summary.ps1    # Tổng hợp % coverage theo từng module
│   ├── inspect-coverage.ps1        # Bảng chi tiết dòng code chưa cover
│   └── inspect-uncovered-files.ps1 # Danh sách file có độ bao phủ thấp nhất
│
├── 📂 trello/                      # [4] Quản Trị Task & Dashboard Trello
│   ├── trello.bat                  # Phím tắt CLI thao tác với Trello
│   ├── trello-cli.js               # Core Node.js xử lý Trello API
│   ├── export-dashboard-data.js    # Xuất dữ liệu snapshot sang file JSON
│   ├── trello-client.mjs           # Module nạp cấu hình & kết nối Trello API dùng chung
│   ├── .trello.env.example         # File mẫu cấu hình Trello API credentials
│   ├── check-list-pos.mjs          # Kiểm tra thứ tự vị trí các danh sách trên Board
│   ├── check-sprint.mjs            # Đối soát danh sách thẻ và thành viên trong Sprint
│   ├── check-sprint-lists.mjs      # Quét chi tiết thẻ theo từng cột Sprint
│   ├── find-sprint3-cards.mjs      # Lọc toàn bộ thẻ thuộc Sprint 03
│   ├── inspect-unfinished.mjs      # Kiểm tra các thẻ chưa hoàn thành trong Sprint 03
│   ├── create-defect-cards.mjs     # Tự động tạo thẻ lỗi / cải tiến UI/UX lên Board
│   └── migrate-to-sprint4.mjs      # Tự động chuyển thẻ tồn đọng sang Sprint 04 Backlog
│
└── 📂 git/                         # [5] Quản Trị Nhánh, Kiểm Toán & Checkout Thông Minh
    ├── git-tool.bat                # Phím tắt thao tác Git nhanh trên Windows
    ├── checkout-helper.js          # Chuyển nhánh thông minh bằng số thứ tự (1, 2, 3...)
    ├── branch-sync-audit.js        # Bảng tổng hợp đối soát Ahead/Behind toàn bộ nhánh
    ├── commit-deep-audit.js        # Kiểm tra chuyên sâu commit & chuẩn Conventional
    └── branch-sync.ps1             # PowerShell script kiểm tra đồng bộ nhánh
```

---

## ⚡ Hướng Dẫn Sử Dụng

### 1. Bảng Điều Khiển Trung Tâm (Launcher Duy Nhất):
File [`smart-parking-microservice/menu.bat`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/menu.bat) được đặt ngay tại thư mục gốc của project để bạn nhấp đúp chạy bất kỳ chức năng nào từ 5 thư mục trên.

Menu hiện thông báo ngay khi nhận lựa chọn; Enter trống không chạy lại tác vụ cũ. Deploy 1.7 nhắc trạng thái mỗi 5 giây. Kiểm tra VPS 1.9 hiển thị ba bước cố định, ký hiệu quay và đồng hồ `mm:ss` cập nhật tại chỗ; chạy xong xóa phần tiến trình, giữ kết quả và mã thoát. Chỉ gõ thêm khi công cụ hiện câu hỏi; xem [hướng dẫn trạng thái deploy](deploy/README.md).

### 2. Chạy Trực Tiếp Từ Terminal:
```bash
# Checkout chuyển nhánh thông minh:
.\scripts\git\git-tool.bat checkout

# Kiểm tra đồng bộ tất cả các nhánh với remote:
.\scripts\git\git-tool.bat audit

# Kiểm tra chuyên sâu commit HEAD từng nhánh:
.\scripts\git\git-tool.bat commit

# Thao tác Trello:
.\scripts\trello\trello.bat members
```

### 3. Cấu Hình & Tự Động Hóa Trello:
```bash
# 1. Sao chép file cấu hình mẫu:
cp scripts/trello/.trello.env.example .trello.env

# 2. Điền TRELLO_API_KEY, TRELLO_API_TOKEN, TRELLO_ACTIVE_BOARD_ID vào .trello.env

# 3. Chạy các kịch bản kiểm toán & tự động hóa Sprint:
node scripts/trello/check-list-pos.mjs            # Xem vị trí các cột
node scripts/trello/find-sprint3-cards.mjs        # Quét thẻ Sprint 03
node scripts/trello/inspect-unfinished.mjs        # Kiểm tra thẻ chưa xong
node scripts/trello/check-risks-improvements.mjs  # Quét rủi ro & cải tiến
node scripts/trello/create-defect-cards.mjs       # Tạo các thẻ defect/UX lên Trello
node scripts/trello/migrate-to-sprint4.mjs        # Chuyển task sang Sprint 04
```

## Compose configuration checks

Run `python scripts/build-test/check-compose.py` (or `scripts/build-test/check-compose.bat`) with Python 3.10+ and Docker Compose. This renders synthetic scenarios without starting containers or reading repository secrets. `start-local-integration.bat` adds the existing Kafka capability to local-dev; other launcher names remain available. See [supported combinations](../docker/compose/README.md).
