@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting Full Local Integration Lab
echo  (Microservices + MySQL + Redis + Kafka + Prometheus + Grafana)
echo ==========================================================

cd ..
docker compose -f compose.yaml -f compose.local.yaml -f compose.local-full.yaml up -d --build
echo Full local lab started!
pause
