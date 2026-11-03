# 🌿 SMARTPARKINGHUB - HỆ THỐNG CÔNG CỤ QUẢN TRỊ & CHECKOUT GIT

Thư mục này chứa bộ công cụ kiểm toán nhánh (Audit), đồng bộ remote và hỗ trợ checkout thông minh dành cho dự án SmartParkingHub Microservices.

---

## 🗂️ Danh Sách Công Cụ

| Tên File | Công Dụng | Cách Dùng Nhanh |
|---|---|---|
| **`checkout-helper.js`** | **Smart Git Checkout:** Chuyển nhánh thông minh bằng số thứ tự (1, 2, 3...) hoặc từ khóa; tự động cảnh báo & stash nếu dở dang; tự động pull code mới. | `node scripts/git/checkout-helper.js` hoặc `.\scripts\git\git-tool.bat` |
| **`branch-sync-audit.js`** | **Branch Sync Audit:** Bảng tổng hợp đối soát Ahead/Behind của toàn bộ các nhánh với remote; nhận diện nhánh đã merge vào main. | `node scripts/git/branch-sync-audit.js` |
| **`commit-deep-audit.js`** | **Commit Deep Audit:** Kiểm tra chuyên sâu commit HEAD từng nhánh, format Conventional Commits và chứng nhận trạng thái sẵn sàng làm việc. | `node scripts/git/commit-deep-audit.js` |
| **`branch-sync.ps1`** | Phiên bản PowerShell tương đương để kiểm tra trạng thái nhánh trên Windows. | `.\scripts\git\branch-sync.ps1` |
| **`git-tool.bat`** | File thực thi nhanh tiện lợi cho người dùng Windows Command Prompt. | `.\scripts\git\git-tool.bat [checkout / audit / commit / ps]` |

---

## 🚀 Hướng Dẫn Sử Dụng

### 1. Chuyển Nhánh Bằng Số Thứ Tự (Khuyên Dùng):
```bash
# Chạy trực tiếp qua batch:
.\scripts\git\git-tool.bat

# Hoặc qua node:
node scripts/git/checkout-helper.js
```
* Hệ thống sẽ hiển thị bảng danh sách các nhánh được đánh số `[1]`, `[2]`, `[3]...` cùng trạng thái đồng bộ:
  * 🟢 Đồng bộ 100%
  * 🟡 Chưa push (Ahead)
  * 🔵 Cần pull (Behind)
  * ⚪ Chưa có upstream
* Bạn chỉ cần gõ số (ví dụ: `2`) rồi nhấn Enter để chuyển ngay!

### 2. Chuyển Nhánh Bằng Từ Khóa Tìm Kiếm:
```bash
node scripts/git/checkout-helper.js booking
# Tự động tìm nhánh khớp (vd: feat/booking-service-reservation) và checkout!
```

### 3. Kiểm Tra Sức Khỏe & Trạng Thái Đồng Bộ Toàn Bộ Nhánh:
```bash
node scripts/git/branch-sync-audit.js
# Hoặc kiểm tra chi tiết từng commit:
node scripts/git/commit-deep-audit.js
```

> [!TIP]
> Bạn cũng có thể mở [`menu.bat`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/menu.bat) tại thư mục gốc và chọn mục **[5] QUẢN TRỊ GIT & KIỂM TRA NHÁNH**!
