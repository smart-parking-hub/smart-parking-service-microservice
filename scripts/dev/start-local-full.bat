@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting Full Local Integration Lab
echo  (Microservices + MySQL + Redis + Kafka + Prometheus + Grafana)
echo ==========================================================

cd /d "%~dp0..\.."
docker compose --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml -f docker\compose\local\compose.local-full.yaml up -d --build
echo Full local lab started!
pause
