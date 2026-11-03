# 📁 SMARTPARKINGHUB - HỆ THỐNG SCRIPTS ĐIỀU PHỐI

Toàn bộ các kịch bản tự động hóa, build, test, vận hành container và quản lý task Trello của dự án đã được sắp xếp gọn gàng vào 4 thư mục chuyên biệt:

---

## 🗂️ Cấu Trúc 4 Nhóm Thư Mục Chuyên Biệt

```
smart-parking-microservice/scripts/
│
├── 📂 dev/                         # [1] Môi Trường & Điều Phối Container Docker
│   ├── start-infra.bat             # Khởi động MySQL, Redis, Kafka, Prometheus, Grafana
│   ├── start-local-dev.bat         # Khởi động hồ sơ Local Dev (hot-reload)
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
│   └── db-migrate-dry-run.bat      # Xác thực cú pháp SQL của tất cả Flyway migrations
│
├── 📂 quality/                     # [3] Chất Lượng Mã Nguồn & Báo Cáo SonarQube
│   ├── start-sonarqube.bat         # Khởi động SonarQube container (Port 9000)
│   ├── sonar-scan.bat              # Chạy test + JaCoCo coverage + Sonar scanner
│   ├── get-coverage-summary.ps1    # Tổng hợp % coverage theo từng module
│   ├── inspect-coverage.ps1        # Bảng chi tiết dòng code chưa cover
│   └── inspect-uncovered-files.ps1 # Danh sách file có độ bao phủ thấp nhất
│
└── 📂 trello/                      # [4] Quản Trị Task & Dashboard Trello
    ├── trello.bat                  # Phím tắt CLI thao tác với Trello
    ├── trello-cli.js               # Core Node.js xử lý Trello API
    └── export-dashboard-data.js    # Xuất dữ liệu snapshot sang file JSON
```

---

## ⚡ Hướng Dẫn Sử Dụng

### 1. Bảng Điều Khiển Trung Tâm (Launcher Duy Nhất):
File [`smart-parking-microservice/menu.bat`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/menu.bat) được đặt ngay tại thư mục gốc của project để bạn nhấp đúp chạy bất kỳ chức năng nào từ 4 thư mục trên.

### 2. Chạy Trực Tiếp Từ Terminal:
```bash
# Thao tác Trello:
.\scripts\trello\trello.bat members
.\scripts\trello\trello.bat status

# Chạy kiểm thử:
.\scripts\build-test\test-all.bat

# Khởi động hạ tầng:
.\scripts\dev\start-infra.bat
```
