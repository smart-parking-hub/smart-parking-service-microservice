# 🐳 SMARTPARKINGHUB - HỆ THỐNG DOCKER COMPOSE PROFILES

Toàn bộ các file cấu hình Docker Compose (`.yaml` / `.yml`) của dự án đã được quy hoạch gọn gàng vào 3 nhóm môi trường chuyên biệt:

---

## 🗂️ Cấu Trúc Phân Chia Thư Mục

```
smart-parking-microservice/docker/compose/
│
├── 📂 local/                         # [1] Môi Trường Phát Triển Cục Bộ (Local Dev)
│   ├── compose.yaml                  # Cấu hình gốc chuẩn 6 microservices
│   ├── compose.local.yaml            # Overlay: Mở port debug và cấu hình local DB
│   ├── compose.local-full.yaml       # Overlay: Đầy đủ Kafka KRaft, Redis, Prometheus, Grafana
│   ├── docker-compose.infra.yml      # Standalone Stack: Chỉ bật tầng Hạ tầng (MySQL, Redis, Kafka)
│   ├── docker-compose.apps.yml       # Standalone Stack: Chỉ bật tầng 6 Microservices
│   └── docker-compose.yml            # Standalone Stack: Tổng hợp cả Apps & Infra
│
├── 📂 vps-single/                    # [2] Môi Trường Máy Chủ Đơn (Single-Node VPS)
│   ├── compose.vps-r4.yaml           # Hồ sơ tối ưu VPS giới hạn RAM (512MB/service, Nginx reverse proxy)
│   └── compose.vps-large.yaml        # Hồ sơ VPS cấu hình cao (Tăng JVM Heap, scale replicas)
│
└── 📂 vps-cluster/                   # [3] Môi Trường Cụm Phân Tán Đa Máy Chủ (Multi-VPS 3 Nodes)
    ├── compose.vps1-edge.yml         # Node 1 (Edge): Nginx Reverse Proxy, SSL Certbot, API Gateway
    ├── compose.vps2-data.yml         # Node 2 (Data): MySQL Cluster, Redis Cache & Kafka Broker
    └── compose.vps3-core.yml         # Node 3 (Core): Toàn bộ Microservices nghiệp vụ
```

---

## 🚀 Hướng Dẫn Khởi Chạy Từng Môi Trường

### 1. Khởi Chạy Local Dev (Máy Cá Nhân):
```bash
# Khởi động Local Dev tiêu chuẩn:
docker compose --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/local/compose.local.yaml up -d --build

# Khởi động Full Local Lab (kèm Kafka, Prometheus, Grafana):
docker compose --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/local/compose.local.yaml -f docker/compose/local/compose.local-full.yaml up -d --build

# Hoặc chỉ khởi động riêng tầng hạ tầng:
cd docker && docker compose -f compose/local/docker-compose.infra.yml up -d
```

### 2. Triển Khai Single-Node VPS:
```bash
# Triển khai hồ sơ VPS R4 (giới hạn RAM 512MB, Nginx):
docker compose --project-directory . -f docker/compose/local/compose.yaml -f docker/compose/vps-single/compose.vps-r4.yaml up -d --build
```

### 3. Triển Khai Cụm Phân Tán (3 VPS):
* **Trên VPS 1 (Edge):** `docker compose -f docker/compose/vps-cluster/compose.vps1-edge.yml up -d`
* **Trên VPS 2 (Data):** `docker compose -f docker/compose/vps-cluster/compose.vps2-data.yml up -d`
* **Trên VPS 3 (Core):** `docker compose -f docker/compose/vps-cluster/compose.vps3-core.yml up -d`

> [!TIP]
> Bạn có thể chạy nhanh tất cả các profile trên bằng file launcher [`menu.bat`](file:///d:/Documents/JAVA%20WEB%20SERVICE/SmartParkingHub/smart-parking-microservice/menu.bat) tại thư mục gốc!
